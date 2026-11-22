package com.cpq.app.pricing.pricelist;

import com.cpq.app.metamodel.EntityInstanceRepository;
import com.cpq.app.pricing.PriceListItemV2Repository;
import com.cpq.app.pricing.PriceListV2Repository;
import com.cpq.app.pricing.dag.AnchorInjector;
import com.cpq.app.pricing.dag.MeasureAwareDagEngine;
import com.cpq.core.api.pricelist.AddPriceListItemRequest;
import com.cpq.core.api.pricelist.CreatePriceListRequest;
import com.cpq.core.api.pricelist.DagNodeDto;
import com.cpq.core.api.pricelist.OverridePriceRequest;
import com.cpq.core.api.pricelist.PriceListDetailDto;
import com.cpq.core.api.pricelist.PriceListDto;
import com.cpq.core.api.pricelist.PriceListItemDto;
import com.cpq.core.api.pricelist.PriceListService;
import com.cpq.core.api.pricelist.TraceEntryDto;
import com.cpq.core.metamodel.EntityInstance;
import com.cpq.core.pricing.dag.DagExecutionResult;
import com.cpq.core.pricing.dag.DagNode;
import com.cpq.core.pricing.dag.TraceEntry;
import com.cpq.core.pricing.persistence.PriceListItemV2Entity;
import com.cpq.core.pricing.persistence.PriceListV2Entity;
import com.cpq.core.pricing.persistence.PriceListV2StateException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code app}-side implementation of {@code core-api}'s {@link
 * PriceListService} port (S3-T6, ADR-009 mục 5, backlog E8-05).
 * Orchestrates: {@link PriceListV2Repository}/{@link
 * PriceListItemV2Repository} (persistence), {@link EntityInstanceRepository}
 * (resolving the Product being priced), {@link AnchorInjector} (baking
 * that Product's id into every top-level {@code query}/{@code
 * measure-ref} node missing one — generalization of D-042/D-045's
 * convention, see {@code AnchorInjector}'s Javadoc and docs/decisions.md
 * D-046), and {@link MeasureAwareDagEngine} (running the patched graph).
 *
 * <p>Status-transition guards ({@code DRAFT}/{@code PENDING_APPROVAL}/
 * {@code APPROVED}) live on {@link PriceListV2Entity} itself and throw
 * {@link PriceListV2StateException} (a plain, framework-free exception,
 * since {@code core-pricing} must stay Spring-MVC-free per ADR-005) —
 * this class is the one place that catches it and rethrows it as a
 * {@link ResponseStatusException} with {@link HttpStatus#CONFLICT} for
 * the HTTP layer.
 */
@Service
public class PriceListServiceImpl implements PriceListService {

    private static final Set<String> NODE_TYPES_NEEDING_ANCHOR = Set.of("query", "measure-ref");

    private final PriceListV2Repository priceListV2Repository;
    private final PriceListItemV2Repository priceListItemV2Repository;
    private final EntityInstanceRepository entityInstanceRepository;
    private final MeasureAwareDagEngine measureAwareDagEngine;

    public PriceListServiceImpl(
            PriceListV2Repository priceListV2Repository,
            PriceListItemV2Repository priceListItemV2Repository,
            EntityInstanceRepository entityInstanceRepository,
            MeasureAwareDagEngine measureAwareDagEngine) {
        this.priceListV2Repository = priceListV2Repository;
        this.priceListItemV2Repository = priceListItemV2Repository;
        this.entityInstanceRepository = entityInstanceRepository;
        this.measureAwareDagEngine = measureAwareDagEngine;
    }

    @Override
    @Transactional
    public PriceListDto createPriceList(CreatePriceListRequest request) {
        List<DagNode> graph = request.calculationGraph().stream().map(PriceListServiceImpl::toDagNode).toList();
        PriceListV2Entity entity =
                new PriceListV2Entity(
                        request.code(),
                        request.name(),
                        request.currency(),
                        request.validFrom(),
                        request.validTo(),
                        graph,
                        request.resultNodeId(),
                        currentPrincipalName());
        priceListV2Repository.save(entity);
        return toDto(entity);
    }

    @Override
    @Transactional
    public PriceListItemDto addItem(Long priceListId, AddPriceListItemRequest request) {
        PriceListV2Entity priceList = requirePriceList(priceListId);
        requireMutable(priceList);

        EntityInstance entityInstance =
                entityInstanceRepository
                        .findById(request.entityInstanceId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unknown entity_instance id: " + request.entityInstanceId()));

        List<DagNode> patchedGraph =
                AnchorInjector.injectAnchorIntoNodesMissingOne(
                        priceList.getCalculationGraph(), entityInstance.getId(), NODE_TYPES_NEEDING_ANCHOR);

        DagExecutionResult executionResult = measureAwareDagEngine.execute(patchedGraph);
        BigDecimal resultPrice = requireBigDecimalResult(priceList, executionResult);

        PriceListItemV2Entity item =
                new PriceListItemV2Entity(
                        priceList, entityInstance.getId(), priceList.getValidFrom(), priceList.getValidTo());
        item.recordCalculationResult(resultPrice, executionResult.trace());
        priceListItemV2Repository.save(item);
        return toDto(item);
    }

    @Override
    @Transactional
    public PriceListDto submit(Long priceListId) {
        PriceListV2Entity priceList = requirePriceList(priceListId);
        try {
            priceList.submit();
        } catch (PriceListV2StateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return toDto(priceList);
    }

    @Override
    @Transactional
    public PriceListDto approve(Long priceListId) {
        PriceListV2Entity priceList = requirePriceList(priceListId);
        try {
            priceList.approve(currentPrincipalName());
        } catch (PriceListV2StateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return toDto(priceList);
    }

    @Override
    @Transactional
    public PriceListItemDto overridePrice(Long itemId, OverridePriceRequest request) {
        PriceListItemV2Entity item =
                priceListItemV2Repository
                        .findById(itemId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Unknown price_list_item_v2 id: " + itemId));
        item.setManualOverridePrice(request.manualOverridePrice());
        return toDto(item);
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListDetailDto getPriceList(Long priceListId) {
        PriceListV2Entity priceList = requirePriceList(priceListId);
        List<PriceListItemV2Entity> items = priceListItemV2Repository.findByPriceList_Id(priceListId);
        List<DagNodeDto> graph =
                priceList.getCalculationGraph().stream().map(PriceListServiceImpl::toDagNodeDto).toList();
        return new PriceListDetailDto(
                priceList.getId(),
                priceList.getCode(),
                priceList.getName(),
                priceList.getCurrency(),
                priceList.getStatus().name(),
                priceList.getValidFrom(),
                priceList.getValidTo(),
                priceList.getResultNodeId(),
                priceList.getVersion(),
                priceList.getCreatedBy(),
                priceList.getSubmittedAt(),
                priceList.getApprovedBy(),
                priceList.getApprovedAt(),
                graph,
                items.stream().map(PriceListServiceImpl::toDto).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceListDto> listPriceLists() {
        return priceListV2Repository.findAllByOrderByIdDesc().stream().map(PriceListServiceImpl::toDto).toList();
    }

    private PriceListV2Entity requirePriceList(Long id) {
        return priceListV2Repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown price_list_v2 id: " + id));
    }

    private void requireMutable(PriceListV2Entity priceList) {
        try {
            priceList.requireMutable();
        } catch (PriceListV2StateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private BigDecimal requireBigDecimalResult(PriceListV2Entity priceList, DagExecutionResult executionResult) {
        Object rawResult = executionResult.results().get(priceList.getResultNodeId());
        if (!(rawResult instanceof BigDecimal resultPrice)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Price list '"
                            + priceList.getCode()
                            + "' result_node_id '"
                            + priceList.getResultNodeId()
                            + "' did not produce a BigDecimal value (got: "
                            + rawResult
                            + ")");
        }
        return resultPrice;
    }

    private static String currentPrincipalName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private static DagNode toDagNode(DagNodeDto dto) {
        return new DagNode(dto.id(), dto.type(), dto.config(), dto.inputs());
    }

    private static DagNodeDto toDagNodeDto(DagNode node) {
        return new DagNodeDto(node.id(), node.type(), node.config(), node.inputs());
    }

    private static PriceListDto toDto(PriceListV2Entity entity) {
        return new PriceListDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCurrency(),
                entity.getStatus().name(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getResultNodeId(),
                entity.getVersion(),
                entity.getCreatedBy(),
                entity.getSubmittedAt(),
                entity.getApprovedBy(),
                entity.getApprovedAt());
    }

    private static PriceListItemDto toDto(PriceListItemV2Entity item) {
        List<TraceEntryDto> waterfallSteps =
                item.getWaterfallSteps() == null ? List.of() : item.getWaterfallSteps().stream().map(PriceListServiceImpl::toDto).toList();
        return new PriceListItemDto(
                item.getId(),
                item.getPriceList().getId(),
                item.getEntityInstanceId(),
                item.getResultPrice(),
                item.getManualOverridePrice(),
                waterfallSteps,
                item.getValidFrom(),
                item.getValidTo());
    }

    private static TraceEntryDto toDto(TraceEntry entry) {
        return new TraceEntryDto(entry.nodeId(), entry.type(), entry.resolvedInputs(), entry.output());
    }
}
