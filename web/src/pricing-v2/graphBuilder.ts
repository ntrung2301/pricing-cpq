import type { DagNodeDto } from './types'

// S3-T8 Measure Canvas: the UI builds a simple, ordered node chain by
// drag-and-drop (not a free-form graph editor — see docs/decisions.md
// D-049 for why) and this module turns it into the real DagNodeDto[] +
// resultNodeId shape `POST /api/price-lists-v2` expects (ADR-009 mục 2:
// a DagNode is pure data — id/type/config/inputs).
//
// Deliberately pure/no DOM/no fetch so it is unit-testable in isolation
// (CLAUDE.md rule 1 — TDD) — see graphBuilder.test.ts, written first.

export type SourceType = 'PRODUCT' | 'CUSTOMER' | 'EXTENSION' | 'PARAMETER'

export type Operand = { type: 'literal'; value: number } | { type: 'ref'; nodeId: string }

export interface SourceCanvasNode {
  id: string
  kind: 'source'
  label: string
  /** An existing CalculationBindingEntity.attributeCode (ADR-009 mục 3). */
  bindingAttributeCode: string
  sourceType: SourceType
  /**
   * Only meaningful (and required in practice) for CUSTOMER sources: a
   * `query` node reading a CUSTOMER binding is never auto-anchored to the
   * Product being priced (AnchorInjector only injects PRODUCT's id, see
   * app's PriceListServiceImpl) — the author must bake a concrete Customer
   * instance id in at authoring time, same convention as
   * PriceListDemoSeedRunner's `q_customer` node.
   */
  anchorInstanceId?: number
}

export interface FormulaCanvasNode {
  id: string
  kind: 'formula'
  label: string
  operator: '+' | '-' | '*' | '/'
  operandA: Operand
  operandB: Operand
}

export interface RoundCanvasNode {
  id: string
  kind: 'round'
  label: string
  nearest: number
  inputNodeId: string
}

export interface OutputCanvasNode {
  id: string
  kind: 'output'
  label: string
  fields: { label: string; nodeId: string }[]
}

export type CanvasNode = SourceCanvasNode | FormulaCanvasNode | RoundCanvasNode | OutputCanvasNode

export class GraphBuildError extends Error {}

export interface BuiltGraph {
  calculationGraph: DagNodeDto[]
  resultNodeId: string
}

/**
 * Converts the canvas's ordered node list into the real DagNodeDto[] +
 * resultNodeId. Nodes must already be in a valid build order — a `ref`
 * operand/input may only point at a node earlier in the array (same
 * "author in dependency order" convention the drag-and-drop canvas
 * enforces by always appending to the end of the chain).
 */
export function buildCalculationGraph(nodes: CanvasNode[]): BuiltGraph {
  if (nodes.length === 0) {
    throw new GraphBuildError('Graph is empty — add at least one calculation node.')
  }

  const seenIds = new Set<string>()
  const calculationGraph: DagNodeDto[] = []
  let resultNodeId: string | null = null

  for (const node of nodes) {
    switch (node.kind) {
      case 'source': {
        const config: Record<string, unknown> = { bindingAttributeCode: node.bindingAttributeCode }
        if (node.anchorInstanceId !== undefined) {
          config.anchorInstanceId = node.anchorInstanceId
        }
        calculationGraph.push({ id: node.id, type: 'query', config, inputs: {} })
        break
      }
      case 'formula': {
        const inputs: Record<string, string> = {}
        const expression = {
          [node.operator]: [
            operandToJsonLogic(node.operandA, 'a', seenIds, inputs),
            operandToJsonLogic(node.operandB, 'b', seenIds, inputs),
          ],
        }
        calculationGraph.push({ id: node.id, type: 'formula', config: { expression }, inputs })
        resultNodeId = node.id
        break
      }
      case 'round': {
        requireSeen(node.inputNodeId, seenIds)
        calculationGraph.push({
          id: node.id,
          type: 'round',
          config: { nearest: node.nearest },
          inputs: { value: node.inputNodeId },
        })
        resultNodeId = node.id
        break
      }
      case 'output': {
        const inputs: Record<string, string> = {}
        for (const field of node.fields) {
          requireSeen(field.nodeId, seenIds)
          inputs[field.label] = field.nodeId
        }
        calculationGraph.push({ id: node.id, type: 'output', config: {}, inputs })
        break
      }
    }
    seenIds.add(node.id)
  }

  if (resultNodeId === null) {
    throw new GraphBuildError(
      'Graph has no numeric (formula/round) node to use as the result — add at least one.',
    )
  }

  return { calculationGraph, resultNodeId }
}

function operandToJsonLogic(
  operand: Operand,
  inputKey: 'a' | 'b',
  seenIds: Set<string>,
  inputs: Record<string, string>,
): unknown {
  if (operand.type === 'literal') {
    return operand.value
  }
  requireSeen(operand.nodeId, seenIds)
  inputs[inputKey] = operand.nodeId
  return { var: inputKey }
}

function requireSeen(nodeId: string, seenIds: Set<string>): void {
  if (!seenIds.has(nodeId)) {
    throw new GraphBuildError(
      `Node '${nodeId}' is referenced before it was added to the graph — add it to the canvas first.`,
    )
  }
}
