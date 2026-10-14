# Architecture Guardrails

## Forbidden dependency directions

- `:feature:*` → `:feature:*` (features never depend on each other)
- `:domain:*` → `:data:network` or `:data:local` (domain only sees repository interface)
- `:core:*` → `:feature:*`, `:data:*`, `:domain:*`
- `:data:models` → any other data module

## Forbidden patterns

- Calling `suspend fun` inside `updateState { it.copy(...) }` — will not compile
- Using `GlobalScope` — use `viewModelScope` via `execute { }`
- Platform SDK imports in `commonMain` source set
- Wildcard imports in Kotlin files
- Magic numbers without named constants (Detekt blocks these)

## Required patterns

- All ViewModels extend `MVIBaseViewModel<S, I>`
- All states implement `MVIBaseState`
- All intents implement `MVIBaseIntent`
- Repository interfaces declared in `:data:repository`, implementations alongside
- Use cases extend `UseCase<P, R>` or `NoParamUseCase<R>`
