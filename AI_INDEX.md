# AI Index

Quick lookup table for agents and AI tools.

| Task | Read first | Key files |
|------|-----------|-----------|
| Add a feature screen | ARCHITECTURE.md, MODULE_MAP.md | `feature/home/` as reference |
| Add a use case | ARCHITECTURE.md (layers) | `domain/usecases/user/GetMeUseCase.kt` |
| Add a repository | MODULE_MAP.md | `data/repository/UserRepository.kt` |
| Add a DB table | `data/local/src/commonMain/sqldelight/*.sq` | `UserEntity.sq` |
| Add a network endpoint | `data/network/api/UserApi.kt` | |
| Debug compilation error | AGENTS.md (rules), ARCHITECTURE.md | |
| Upgrade a library | `gradle/libs.versions.toml` | Update version + rebuild |
| Add CI step | `.github/workflows/validate.yml` | |
| Change theme | `core/ui/src/commonMain/.../theme/` | `Color.kt`, `Theme.kt` |
| Add platform-specific code | ARCHITECTURE.md (expect/actual section) | `core/security/` as reference |
