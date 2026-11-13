// Mirrors the backend's generic configurator DTO (core-api's
// com.cpq.core.api.configurator.CompatibleInstanceSummary), S2-T8
// (docs/decisions.md D-027). Generic: "this instance has a passing
// compatibility constraint with your anchor instance" — never "this part
// fits this vehicle".

export interface CompatibleInstanceSummary {
  entityInstanceId: number
  businessKey: string
  displayLabel: string
}
