package com.cpq.app.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cpq.app.metamodel.EntityDefinitionRepository;
import com.cpq.core.metamodel.AttributeDataType;
import com.cpq.core.metamodel.AttributeDefinition;
import com.cpq.core.metamodel.EntityDefinition;
import com.cpq.core.metamodel.EntityRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Plain-unit (mocked {@link EntityDefinitionRepository}, no Spring context,
 * no DB) test for {@link CoreAnchorBootstrapService} (S3-T1, E8-01,
 * ADR-009 mục 1, D-039/D-040).
 *
 * <p>Deliberately NOT a {@code @SpringBootTest}/Testcontainers IT: this
 * service's whole job only matters *before* any pack has ever loaded, but
 * a full {@code @SpringBootTest} context always runs **every**
 * {@code ApplicationRunner} bean in {@code app} automatically at context
 * startup — including {@code PackLoaderService}, which loads the real
 * automotive {@code pack.yaml} onto {@code Product}/{@code Customer}
 * before any {@code @Test} method body executes (confirmed empirically:
 * calling {@code bootstrapCoreAnchors()} as the *only* statement of an
 * otherwise-isolated {@code @SpringBootTest} method still found {@code
 * Product} already carrying automotive's real attributes — see D-040,
 * which corrects D-020 point 6's "ApplicationRunner KHÔNG tự chạy dưới
 * @SpringBootTest" claim for the environment/Spring Boot version this
 * repo currently runs on). A "DB rỗng, chưa pack nào chạy" state this
 * service's contract actually depends on is therefore only constructible
 * with a mocked repository, not a real Testcontainers Postgres reached
 * through the full application context. The *integration* path (real
 * Postgres, real {@code role} column round-trip through JPA) is already
 * covered by {@code AutomotivePackIT}, which calls
 * {@code bootstrapService.bootstrapCoreAnchors()} against a real container
 * and asserts {@code role == ANCHOR} afterwards.
 */
class CoreAnchorBootstrapServiceTest {

    @Test
    void createsProductAndCustomerAnchorShellsWithNoAttributesWhenNeitherExistsYet() {
        EntityDefinitionRepository repository = mock(EntityDefinitionRepository.class);
        when(repository.findByCodeWithAttributes(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new CoreAnchorBootstrapService(repository).bootstrapCoreAnchors();

        EntityDefinition savedProduct = captureSaved(repository, "Product");
        assertThat(savedProduct.getCode()).isEqualTo("Product");
        assertThat(savedProduct.getLabel()).isEqualTo("Product");
        assertThat(savedProduct.getVersion()).isEqualTo(1);
        assertThat(savedProduct.getRole()).isEqualTo(EntityRole.ANCHOR);
        assertThat(savedProduct.getAttributes()).isEmpty();

        EntityDefinition savedCustomer = captureSaved(repository, "Customer");
        assertThat(savedCustomer.getCode()).isEqualTo("Customer");
        assertThat(savedCustomer.getLabel()).isEqualTo("Customer");
        assertThat(savedCustomer.getVersion()).isEqualTo(1);
        assertThat(savedCustomer.getRole()).isEqualTo(EntityRole.ANCHOR);
        assertThat(savedCustomer.getAttributes()).isEmpty();
    }

    @Test
    void doesNotTouchAttributesOfAnAlreadyExistingProductAndOnlyEnsuresItsRole() {
        EntityDefinition existingProduct = new EntityDefinition("Product", "Product (pre-existing)", 1);
        existingProduct.addAttribute(new AttributeDefinition("sku", "SKU", AttributeDataType.STRING, true, null));
        EntityDefinitionRepository repository = mock(EntityDefinitionRepository.class);
        when(repository.findByCodeWithAttributes("Product")).thenReturn(Optional.of(existingProduct));
        when(repository.findByCodeWithAttributes("Customer")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new CoreAnchorBootstrapService(repository).bootstrapCoreAnchors();

        assertThat(existingProduct.getRole()).isEqualTo(EntityRole.ANCHOR);
        assertThat(existingProduct.getAttributes()).hasSize(1);
        assertThat(existingProduct.getAttributes().get(0).getCode()).isEqualTo("sku");
        assertThat(existingProduct.getLabel()).isEqualTo("Product (pre-existing)"); // untouched
    }

    @Test
    void isIdempotentWhenCalledTwiceInARow() {
        EntityDefinitionRepository repository = mock(EntityDefinitionRepository.class);
        when(repository.findByCodeWithAttributes(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CoreAnchorBootstrapService service = new CoreAnchorBootstrapService(repository);
        service.bootstrapCoreAnchors();
        service.bootstrapCoreAnchors();

        verify(repository, org.mockito.Mockito.times(2)).findByCodeWithAttributes(eq("Product"));
        verify(repository, org.mockito.Mockito.times(2)).findByCodeWithAttributes(eq("Customer"));
    }

    private static EntityDefinition captureSaved(EntityDefinitionRepository repository, String code) {
        org.mockito.ArgumentCaptor<EntityDefinition> captor = org.mockito.ArgumentCaptor.forClass(EntityDefinition.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(def -> def.getCode().equals(code))
                .reduce((first, second) -> second) // last save() call wins, matches real behaviour
                .orElseThrow();
    }
}
