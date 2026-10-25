# Auditoria de segurança — 2026-10-25

## Escopo e resultado

Auditoria limitada às dependências Java do bot, às imagens de build/runtime da aplicação e à inspeção read-only da configuração Kubernetes do próprio bot. Não foram consultados dados de usuários, mensagens ou segredos.

- **Java:** corrigidos os achados de runtime; o SBOM CycloneDX final tem 199 componentes resolvidos de `runtimeClasspath`. Trivy 0.76.1 não reportou vulnerabilidades conhecidas em nenhuma severidade nesse SBOM.
- **Imagem:** baseline Debian 12 slim e imagem final Distroless Debian 13 foram construídos e comparados. A imagem final não tem achados HIGH/CRITICAL; permanecem achados LOW/MEDIUM do SO sem versão corrigida indicada pelo Trivy.
- **Smoke HTTP nativo:** **não aprovado**. O executável iniciou e carregou o contexto Spring até o inicializador Flyway, mas tentou conectar ao PostgreSQL e falhou antes de abrir HTTP. Não foram usadas credenciais ou serviços reais; sem um banco de teste permitido/disponível não foi possível confirmar a sonda HTTP.
- **Kubernetes/Flux:** não foi possível inspecionar o Deployment ou as políticas vivas: não há manifests Kubernetes/Flux no repositório e `kubectl`/`flux` não estão disponíveis neste ambiente. Nenhum manifesto foi alterado ou aplicado.

## Grafo Java efetivo

O SBOM foi gerado pelo plugin CycloneDX Gradle 3.5.1, selecionando somente `runtimeClasspath`, depois da resolução transitiva do Gradle 9.8.2. Isso exclui as dependências de teste; não é uma leitura das versões declaradas sem resolução. O binário nativo também informou um SBOM CycloneDX embutido com 151 componentes.

| Advisory | Dependência/versão vulnerável no grafo inicial | Severidade reportada | Faixa afetada e versão corrigida | Presença no runtime e remediação |
|---|---|---|---|---|
| [CVE-2026-41001 / GHSA-ggg2-9786-hwc8](https://osv.dev/vulnerability/CVE-2026-41001) | `spring-boot-autoconfigure` 4.0.6 | MEDIUM, CVSS 5.3 | Boot 4.0.0–4.0.6; corrigido em 4.0.7 | Componente no runtime; atualizado pelo BOM para Boot 4.0.9. O caminho Artemis requer classes Artemis, ausentes no grafo deste serviço. |
| [CVE-2026-41848 / GHSA-659m-px2c-25wj](https://osv.dev/vulnerability/CVE-2026-41848) | `spring-core` 7.0.7 | HIGH pelo NVD (7.5); o GHSA também apresenta score 3.7 | Spring Framework 7.0.0–7.0.7; corrigido em 7.0.8 | Transitivo e no runtime; Boot 4.0.9 fornece Spring Framework 7.0.10. |
| [CVE-2026-41727 / GHSA-53w6-v7cv-fc9h](https://osv.dev/vulnerability/CVE-2026-41727) | `spring-kafka` 4.0.5 | MEDIUM, CVSS 6.5 | Spring Kafka 4.0.0–4.0.5; corrigido em 4.0.6 | Transitivo e no runtime; Spring Kafka 4.0.8 via BOM. |
| [CVE-2026-41731 / GHSA-xq69-5h5v-x9x4](https://osv.dev/vulnerability/CVE-2026-41731) | `spring-kafka` 4.0.5 | HIGH, CVSS 8.1 | Spring Kafka 4.0.0–4.0.5; corrigido em 4.0.6 | Transitivo e no runtime; Spring Kafka 4.0.8 via BOM. |
| [CVE-2026-41726 / GHSA-xvfq-4q6q-gxx7](https://osv.dev/vulnerability/CVE-2026-41726) | `spring-kafka` 4.0.5 | MEDIUM, CVSS 6.5 | Spring Kafka 4.0.0–4.0.5; corrigido em 4.0.6 | Transitivo e no runtime; Spring Kafka 4.0.8 via BOM. O vetor de risco requer o uso de `DelegatingDeserializer`. |
| [CVE-2026-106449 / GHSA-343h-94h5-c4wr](https://osv.dev/vulnerability/CVE-2026-106449) | `at.yawk.lz4:lz4-java` 1.10.1 | LOW, CVSS 3.7 | Anterior a 1.11.4; corrigido em 1.11.4 | Transitivo pelo `kafka-clients` 4.1.2 e no runtime; override para 1.11.4. |
| [CVE-2026-106452 / GHSA-4v53-57pg-c464](https://osv.dev/vulnerability/CVE-2026-106452) | `at.yawk.lz4:lz4-java` 1.10.1 | MEDIUM, CVSS 5.3 | Anterior a 1.11.2; corrigido em 1.11.2 | Mesmo caminho de runtime; override para 1.11.4. |
| [CVE-2026-106453 / GHSA-6cx8-rjf8-pr8g](https://osv.dev/vulnerability/CVE-2026-106453) | `at.yawk.lz4:lz4-java` 1.10.1 | MEDIUM, CVSS 5.3 | Anterior a 1.11.2; corrigido em 1.11.2 | Mesmo caminho de runtime; override para 1.11.4. |
| [CVE-2026-106450 / GHSA-gm45-99xc-r7wv](https://osv.dev/vulnerability/CVE-2026-106450) | `at.yawk.lz4:lz4-java` 1.10.1 | MEDIUM, CVSS 5.3 | Anterior a 1.11.4; corrigido em 1.11.4 | Mesmo caminho de runtime; override para 1.11.4. |
| [CVE-2026-106451 / GHSA-mcr4-qmvw-px4g](https://osv.dev/vulnerability/CVE-2026-106451) | `at.yawk.lz4:lz4-java` 1.10.1 | HIGH, CVSS 4.0 7.3 | Anterior a 1.11.4; corrigido em 1.11.4 | Mesmo caminho de runtime; override para 1.11.4. A exploração descrita pressupõe diretório temporário compartilhado e atacante local. |
| [CVE-2026-59949 / GHSA-xx22-p4ch-683r](https://osv.dev/vulnerability/CVE-2026-59949) | `at.yawk.lz4:lz4-java` 1.10.1 | MEDIUM, CVSS 6.5 | Anterior a 1.11.1; corrigido em 1.11.1 | Mesmo caminho de runtime; override para 1.11.4. |
| [CVE-2026-75140 / GHSA-65r4-943x-97jj](https://osv.dev/vulnerability/CVE-2026-75140) | `org.jsoup:jsoup` 1.21.1 | HIGH, CVSS 3.1 7.5 / CVSS 4.0 8.7 | Versões até 1.23.1 afetadas; fix upstream no commit `862ba2f`, incluído na release 1.23.2 | Dependência direta e no runtime; atualizado para 1.23.2. A descrição textual do OSV/NVD diverge sobre 1.23.2; comparei o commit fixado com a tag upstream e confirmei que a tag contém o fix. Trivy também não reporta o CVE em 1.23.2. |
| [CVE-2026-45292](https://osv.dev/vulnerability/CVE-2026-45292) | OpenTelemetry API e propagator 1.55.0 | MEDIUM, CVSS 5.3; o NVD também publica score 7.5 | Anterior a 1.62.0; corrigido em 1.62.0 | Transitivos e no runtime; importado `io.opentelemetry:opentelemetry-bom:1.62.0`, alinhando todos os módulos OTel. |

O override do LZ4 está limitado à constraint `at.yawk.lz4:lz4-java:1.11.4`: o grafo confirmou a origem pelo Kafka e nenhum BOM importado corrigia essa versão. Os demais upgrades vieram dos BOMs, não de pins individuais.

O `spring-cloud-dependencies:2025.1.4` publicado declara `spring-boot.version=4.0.9`; essa compatibilidade foi verificada no POM do BOM. A resolução final trouxe Boot 4.0.9, Spring Framework 7.0.10, Spring Kafka 4.0.8, Consul 5.0.4, Tomcat 11.0.27, Jackson 2.21.7, Netty 4.2.19 e Micrometer 1.16.8. O driver **cliente** JDBC PostgreSQL ficou em 42.7.14 pelo BOM; isso não atualiza o servidor PostgreSQL.

### Referências Boot 4.0.5

As advisories mencionadas na solicitação também foram conferidas: [CVE-2026-40976](https://osv.dev/vulnerability/CVE-2026-40976), CRITICAL (9.1), e [CVE-2026-40973](https://osv.dev/vulnerability/CVE-2026-40973), HIGH (7.0), afetam Boot até 4.0.5 e são corrigidas em 4.0.6. O grafo inicial já estava em Boot 4.0.6, portanto não estava vulnerável a esses dois CVEs; no caso do CVE-40976, a condição adicional de ausência de `spring-boot-health` também não se aplica ao serviço. O upgrade para 4.0.9 foi feito por compatibilidade com o BOM atual e para corrigir outros achados, não por esses dois CVEs.

## Imagem Docker

O Dockerfile efetivamente destinado à imagem do bot é o `Dockerfile` raiz: compila Native Image em `container-registry.oracle.com/graalvm/native-image:25` e copia somente o binário ao runtime. O estágio de build agora executa `microdnf -y upgrade` e limpa os metadados. O runtime foi trocado de Debian 12 slim para `gcr.io/distroless/cc-debian13:nonroot`, que fornece glibc, libstdc++, zlib e certificados sem shell, gerenciador de pacotes ou ferramentas de build. A release força pull das bases antes de construir.

| Scan Trivy 0.76.1 | Tamanho | Pacotes OS | Findings (registros) | CVEs únicos |
|---|---:|---:|---:|---:|
| Baseline, Debian 12.15 | 748,931,584 bytes (≈714 MiB) | 91 | 243: 1 CRITICAL, 58 HIGH, 95 MEDIUM, 87 LOW, 2 UNKNOWN | 107 |
| Final, Debian 13.7 Distroless | 358,465,003 bytes (≈342 MiB) | 14 | 31: 0 HIGH/CRITICAL, 23 MEDIUM, 8 LOW | 25 |

A redução de tamanho é de aproximadamente 52%. O scan final e o gate HIGH/CRITICAL passaram. A imagem final contém somente o runtime Distroless e o executável; o usuário configurado é `1001:1001`. O scan do binário nativo não substitui o scan Java: as bibliotecas foram verificadas separadamente pelo SBOM de runtime.

### Estágio de build

O scan inicial do builder encontrou 20 registros Java (12 HIGH e 8 MEDIUM; 16 CVEs únicos) em bibliotecas da distribuição Gradle 9.7.1 e no cache `.gradle`. Eram **build-only**, não faziam parte do SBOM runtime nem da imagem publicada:

| Severidade | Advisories | Dependência/versão no builder | Correções indicadas pelos advisories |
|---|---|---|---|
| HIGH | CVE-2026-89407, CVE-2026-89425 | Jackson Core 2.22.0/2.22.2 | 2.22.3 |
| HIGH | CVE-2026-68497, CVE-2026-91776, CVE-2026-91777 | Jackson Databind 2.22.0/2.22.2 | 2.22.2–2.22.3, conforme CVE |
| HIGH | CVE-2026-54399, CVE-2026-54428 | HttpCore5/HttpCore5-H2 5.3.6 | 5.4.3 |
| HIGH | CVE-2026-75140 | jsoup 1.22.2 | 1.23.2 |
| MEDIUM | CVE-2026-19032, CVE-2026-54515, CVE-2026-59889, CVE-2026-77310, CVE-2026-83557 | Jackson Databind 2.22.0 | 2.22.1–2.22.2, conforme CVE |
| MEDIUM | CVE-2025-48924 | Commons Lang 3.16.0 | 3.18.0 |
| MEDIUM | CVE-2026-64607 | HttpClient5 5.5.2 | 5.6.3 |
| MEDIUM | CVE-2026-71497 | jsoup 1.22.2 | 1.23.1 |

O Wrapper foi atualizado para Gradle 9.8.2; o SHA-256 oficial da distribuição (`21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`) foi verificado. A distribuição atualiza Jackson para 2.22.3 e Commons Lang para 3.20.0. O Dockerfile agora compila numa única camada e remove `/root/.gradle` ao final, evitando manter a distribuição e os caches temporários na imagem builder. O scan posterior encontrou **zero** achados em 148 pacotes Oracle Linux e 402 componentes Java. O builder caiu de 2,074,192,105 para 1,671,078,862 bytes.

### Achados OS ainda sem correção indicada

O Trivy não lista `FixedVersion` para os 31 registros restantes da base Debian 13. Eles são preservados no relatório completo; não foram ignorados pelo gate. CVEs únicos:

- **MEDIUM:** CVE-2026-102010, CVE-2026-18374, CVE-2026-19499, CVE-2026-19542, CVE-2026-27171, CVE-2026-5435, CVE-2026-6238, CVE-2026-6368, CVE-2026-6791, CVE-2026-77117, CVE-2026-80489, CVE-2026-85091, CVE-2026-8674, CVE-2026-86805, CVE-2026-89092, CVE-2026-95619, CVE-2026-95818.
- **LOW:** CVE-2010-4756, CVE-2018-20796, CVE-2019-1010022, CVE-2019-1010023, CVE-2019-1010024, CVE-2019-1010025, CVE-2019-9192, CVE-2026-97399.

O scan completo (incluindo achados sem fix) fica disponível como artefato no release. A política bloqueia somente HIGH/CRITICAL conforme solicitado.

## CI e publicação

- `build.gradle` gera SBOM JSON/XML somente de `runtimeClasspath` em `build/reports/cyclonedx-direct/`.
- CI e release usam `aquasecurity/trivy-action` fixada no SHA `ed142fd0673e97e23eac54620cfb913e5ce36c25` (v0.36.0) e Trivy v0.76.1. Há relatório completo e gate separado para HIGH/CRITICAL, com `scanners: vuln` e sem ignorar findings sem fix.
- CI publica SBOM e relatório de dependências como artefato. Release gera o SBOM, faz scan das dependências, constrói e escaneia o estágio builder local, constrói a imagem runtime, escaneia OS/bibliotecas e só então autentica e publica. O estágio builder não é publicado.
- Nenhum commit, tag Git, push ou publicação foi feito durante esta auditoria. O workflow de release publica a tag semver **e** `latest`; se uma política Flux acompanhar uma delas, uma publicação futura pode disparar rollout. Não foi possível confirmar a política atual.

## Validação e limitações

- `./gradlew test cyclonedxDirectBom`: **BUILD SUCCESSFUL**, 273 testes, 256 aprovados, 0 falhas e 17 pulados. Os 17 são testes de integração `@RequiresDocker`; não havia daemon Docker/socket disponível. Portanto Testcontainers não iniciou PostgreSQL, Kafka/Redpanda ou outros serviços.
- Build nativo final no Dockerfile: **BUILD SUCCESSFUL**, 4m30s em `nativeCompile`, pico de RSS 10.76 GB com 16 threads; sem OOM. O binário reportou SBOM nativo embutido com 151 componentes. Não ocorreu `MissingReflectionRegistrationError`, então nenhum hint de reflection foi adicionado.
- O smoke test executou a imagem como UID 1001, com rootfs read-only, sem capabilities e sem privilégios adicionais. O executável iniciou, mas o contexto Spring falhou no inicializador Flyway ao tentar conectar a `127.0.0.1:1`; o HTTP e a probe de liveness não foram alcançados. A configuração `spring.flyway.enabled=false` em runtime não removeu esse caminho no binário AOT. **Smoke HTTP não aprovado**; não usei serviço PostgreSQL nem credenciais reais.
- Há configuração de probes na aplicação (`management.endpoint.health.probes.enabled=true`), mas não há manifests Kubernetes do bot no checkout. Sem `kubectl` e `flux`, imagem/tag do Deployment, `securityContext`, probes aplicadas e políticas Flux permanecem não verificados.
- O arquivo `docker/postgres/Dockerfile` foi mantido intocado. Nenhum servidor PostgreSQL, Redis, Kafka/Redpanda, volume, StatefulSet ou serviço compartilhado foi atualizado ou escaneado nesta tarefa.

## Arquivos alterados

- `build.gradle`
- `Dockerfile`
- `.github/workflows/ci.yml`
- `.github/workflows/release.yml`
- `README.md`
- `gradle/wrapper/gradle-wrapper.properties`
- `SECURITY_AUDIT_2026-10.md` (este relatório)
