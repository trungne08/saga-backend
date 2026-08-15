# Architecture index

Implementation guide:

- Read [unit_graph.yaml](/E:/Semester%209/do_an/SAGA-BE/.github/modernize/rearchitecture/artifacts/unit_graph.yaml) first.
- For each listed unit, read its `behavior.yaml`, `bindings.yaml`, and `unit_decomposition.yaml` under `units/<unit-name>/`.
- Use the global files (`wire_contracts.yaml`, `shared_modules.yaml`, `cross_unit_state.yaml`, `seams.yaml`) as cross-cutting filters, not as a replacement for unit-level behavior contracts.
- Completion evidence for implementation must confirm that each unit's preserved side effects, auth requirements, error paths, and external contract expectations remain unchanged relative to the source.

Provided unit artifact paths:
- `units/auth-controller/behavior.yaml`
- `units/personal-integration-controller/behavior.yaml`
- `units/project-integration-controller/behavior.yaml`
- `units/webhook-controller/behavior.yaml`
- `units/jira-callback-controller/behavior.yaml`
- `units/project-integration-callback-controller/behavior.yaml`
- `units/identity-mapping-review-controller/behavior.yaml`
- `units/team-roster-controller/behavior.yaml`
- `units/team-project-controller/behavior.yaml`
- `units/course-controller/behavior.yaml`
- `units/semester-controller/behavior.yaml`
- `units/class-controller/behavior.yaml`
- `units/subject-controller/behavior.yaml`

Note: this index is a navigation contract, not a full behavior specification. Implementation agents must read the concrete artifact paths before modifying the code.
