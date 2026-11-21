import { describe, expect, it } from 'vitest'
import { buildCalculationGraph, GraphBuildError, type CanvasNode } from './graphBuilder'

// S3-T8: pure logic that turns the Measure Canvas's UI-level node chain
// into the real DagNodeDto[] + resultNodeId the backend
// (POST /api/price-lists-v2, ADR-009 mục 2) expects. Tested in isolation
// from the DOM/API per CLAUDE.md's TDD rule — "Viết test trước khi code UI
// logic quan trọng".

describe('buildCalculationGraph', () => {
  it('builds a single formula node from two literals and picks it as resultNodeId', () => {
    const nodes: CanvasNode[] = [
      {
        id: 'cost',
        kind: 'formula',
        label: 'Base cost',
        operator: '+',
        operandA: { type: 'literal', value: 0 },
        operandB: { type: 'literal', value: 50 },
      },
    ]

    const { calculationGraph, resultNodeId } = buildCalculationGraph(nodes)

    expect(resultNodeId).toBe('cost')
    expect(calculationGraph).toEqual([
      {
        id: 'cost',
        type: 'formula',
        config: { expression: { '+': [0, 50] } },
        inputs: {},
      },
    ])
  })

  it('chains a formula referencing an earlier node, then a round node, and picks the round node as resultNodeId', () => {
    const nodes: CanvasNode[] = [
      {
        id: 'cost',
        kind: 'formula',
        label: 'Base cost',
        operator: '+',
        operandA: { type: 'literal', value: 0 },
        operandB: { type: 'literal', value: 50 },
      },
      {
        id: 'markup',
        kind: 'formula',
        label: 'Margin markup',
        operator: '*',
        operandA: { type: 'ref', nodeId: 'cost' },
        operandB: { type: 'literal', value: 1.3 },
      },
      {
        id: 'rounded',
        kind: 'round',
        label: 'Round to nearest',
        nearest: 1,
        inputNodeId: 'markup',
      },
    ]

    const { calculationGraph, resultNodeId } = buildCalculationGraph(nodes)

    expect(resultNodeId).toBe('rounded')
    expect(calculationGraph).toEqual([
      { id: 'cost', type: 'formula', config: { expression: { '+': [0, 50] } }, inputs: {} },
      {
        id: 'markup',
        type: 'formula',
        config: { expression: { '*': [{ var: 'a' }, 1.3] } },
        inputs: { a: 'cost' },
      },
      { id: 'rounded', type: 'round', config: { nearest: 1 }, inputs: { value: 'markup' } },
    ])
  })

  it('maps a source node to a query DagNode with its bindingAttributeCode, and excludes output nodes from being the resultNodeId', () => {
    const nodes: CanvasNode[] = [
      {
        id: 'q_country',
        kind: 'source',
        label: 'Country of Manufacture',
        bindingAttributeCode: 'plv2_product_country_of_manufacture',
        sourceType: 'PRODUCT',
      },
      {
        id: 'cost',
        kind: 'formula',
        label: 'Base cost',
        operator: '+',
        operandA: { type: 'literal', value: 0 },
        operandB: { type: 'literal', value: 50 },
      },
      {
        id: 'summary',
        kind: 'output',
        label: 'Summary',
        fields: [
          { label: 'price', nodeId: 'cost' },
          { label: 'country', nodeId: 'q_country' },
        ],
      },
    ]

    const { calculationGraph, resultNodeId } = buildCalculationGraph(nodes)

    expect(resultNodeId).toBe('cost')
    expect(calculationGraph).toEqual([
      {
        id: 'q_country',
        type: 'query',
        config: { bindingAttributeCode: 'plv2_product_country_of_manufacture' },
        inputs: {},
      },
      { id: 'cost', type: 'formula', config: { expression: { '+': [0, 50] } }, inputs: {} },
      {
        id: 'summary',
        type: 'output',
        config: {},
        inputs: { price: 'cost', country: 'q_country' },
      },
    ])
  })

  it('bakes an explicit anchorInstanceId into a CUSTOMER source node (never auto-anchored to the Product being priced)', () => {
    const nodes: CanvasNode[] = [
      {
        id: 'q_customer',
        kind: 'source',
        label: 'Customer Tier',
        bindingAttributeCode: 'plv2_customer_tier',
        sourceType: 'CUSTOMER',
        anchorInstanceId: 42,
      },
      {
        id: 'cost',
        kind: 'formula',
        label: 'Base cost',
        operator: '+',
        operandA: { type: 'literal', value: 0 },
        operandB: { type: 'literal', value: 50 },
      },
    ]

    const { calculationGraph } = buildCalculationGraph(nodes)

    expect(calculationGraph[0]).toEqual({
      id: 'q_customer',
      type: 'query',
      config: { bindingAttributeCode: 'plv2_customer_tier', anchorInstanceId: 42 },
      inputs: {},
    })
  })

  it('throws GraphBuildError when a formula node references a node id that was not added before it', () => {
    const nodes: CanvasNode[] = [
      {
        id: 'markup',
        kind: 'formula',
        label: 'Margin markup',
        operator: '*',
        operandA: { type: 'ref', nodeId: 'cost' },
        operandB: { type: 'literal', value: 1.3 },
      },
    ]

    expect(() => buildCalculationGraph(nodes)).toThrow(GraphBuildError)
    expect(() => buildCalculationGraph(nodes)).toThrow(/cost/)
  })

  it('throws GraphBuildError when the chain has no numeric (formula/round) node to use as resultNodeId', () => {
    const nodes: CanvasNode[] = [
      {
        id: 'q_country',
        kind: 'source',
        label: 'Country of Manufacture',
        bindingAttributeCode: 'plv2_product_country_of_manufacture',
        sourceType: 'PRODUCT',
      },
    ]

    expect(() => buildCalculationGraph(nodes)).toThrow(GraphBuildError)
    expect(() => buildCalculationGraph(nodes)).toThrow(/result/i)
  })

  it('throws GraphBuildError on an empty graph', () => {
    expect(() => buildCalculationGraph([])).toThrow(GraphBuildError)
  })
})
