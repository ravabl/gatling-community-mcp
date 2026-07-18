# gatling-community-mcp

[English](README.md) | **Русский**

[![Java 25](https://img.shields.io/badge/Java-25-blue)](https://openjdk.org/projects/jdk/25/)
[![Maven](https://img.shields.io/badge/build-Maven-blue)](https://maven.apache.org/)
[![Container image](https://img.shields.io/badge/container-ghcr.io%2Fravabl%2Fgatling--community--mcp-blue)](https://github.com/ravabl/gatling-community-mcp/pkgs/container/gatling-community-mcp)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

`gatling-community-mcp` — неофициальный локальный MCP-сервер для разработки
сценариев Gatling Community. Он помогает создавать, проверять и валидировать
Gatling-скрипты с помощью локальных MCP-совместимых LLM и агентов.

Сопровождающий: [ravabl](https://github.com/ravabl).

## Быстрый старт

### Docker

Запустите локальный MCP HTTP-сервер и подключите каталог проекта только для
чтения:

```bash
docker pull ghcr.io/ravabl/gatling-community-mcp:0.4.0
docker run --rm -d \
  --name mcp-gatling-community \
  --publish 127.0.0.1:8765:8765 \
  --env GATLING_MCP_WORKSPACE_ROOTS=/workspace \
  --mount "type=bind,source=/absolute/path/to/project,target=/workspace,readonly" \
  ghcr.io/ravabl/gatling-community-mcp:0.4.0 \
  http --bind 0.0.0.0 --port 8765
curl -fsS http://127.0.0.1:8765/healthz
```

Адрес MCP: `http://127.0.0.1:8765/mcp`. Замените путь источника на абсолютный
путь к проекту Gatling. Конфигурация клиента для stdio на переднем плане и
режим bridge описаны ниже.

### Standalone JAR

Требуется Java 25. Загрузите JAR выпуска и запустите тот же локальный HTTP-режим:

```bash
curl -fLO https://github.com/ravabl/gatling-community-mcp/releases/download/v0.4.0/gatling-community-mcp-0.4.0-all.jar
java -version
java -jar gatling-community-mcp-0.4.0-all.jar http --bind 127.0.0.1 --port 8765
```

Для MCP-клиента, запускающего локальную команду на переднем плане, используйте
`java -jar gatling-community-mcp-0.4.0-all.jar` без аргументов `http`. Клиент
должен владеть потоками `stdin` и `stdout` процесса.

## Возможности

- Определяет возможности Gatling Community для версий с `3.7` по `3.15`.
- Распознаёт Gatling-проекты на Maven, Gradle, sbt, npm и TypeScript npm.
- Определяет контекст проекта: каталоги исходного кода и тестов, версию
  Gatling, версии Java и Node, существующие сценарии, зависимости, состояние
  плагинов сообщества, имена пакетов и принятый стиль кода.
- Генерирует HTTP-сценарии для Java, Kotlin, Scala, JavaScript и TypeScript.
- Генерирует проверенные примеры плагинов сообщества для JVM: Kafka, JDBC,
  AMQP и Picatinny.
- Предоставляет сведения о возможностях WebSocket, SSE, JMS, MQTT и gRPC;
  полная генерация кода для этих пяти протоколов отключена.
- Импортирует OpenAPI JSON/YAML, HAR, curl и Postman Collection в
  структурированные планы HTTP-сценариев.
- Анализирует локальные отчёты Gatling и журналы выполнения, выявляя
  неуспешные запросы, медленные перцентили и распространённые эксплуатационные
  ошибки.
- Объясняет ошибки Gatling и с помощью специализированных инструментов анализа
  первопричин (RCA)
  проверяет риски источников данных (`feeders`), корреляции, проверок
  (`assertions`) и модели нагрузки.
- Анализирует сценарии и выявляет распространённые ошибки разработки.
- Перечисляет и объясняет методы DSL, валидирует упорядоченные цепочки методов
  и предлагает известные замены для переименованных или удалённых Gatling API.
- Выполняет проверки компиляции проекта через разрешённые команды Maven,
  Gradle, sbt или npm в изолированной временной копии.
- Поддерживает возможности взаимодействия MCP для совместимых клиентов:
  уведомления о ходе выполнения, запрос недостающих параметров планирования и
  обращение к модели клиента за рекомендательной проверкой.
- Предоставляет расширенные инструменты MCP со строгими `inputSchema`,
  строгими `outputSchema` для успешных результатов и ошибок, подробными
  метаданными, аргументами `prompts`, шаблонами ресурсов и вариантами
  автодополнения для
  локальной разработки.

## Интерфейс MCP

Сервер предоставляет:

- Инструменты с `title`, специализированными описаниями для Gatling, строгими
  `inputSchema`, строгими контрактами `outputSchema` для успешных результатов
  и ошибок, а также аннотациями `non-destructive`. Большинство инструментов
  доступны только для чтения; `gatling_compile_check` явно помечен как
  `non-destructive`, но не как `read-only`, поскольку выполняет команду компиляции
  во временной копии.
- Ресурсы по истории версий, совместимости, каталогам методов, плагинам
  сообщества, приёмам разработки, импорту и эталонным HTTP-потокам.
- Шаблоны ресурсов, например `gatling://methods/http/{language}/{version}`,
  `gatling://versions/{version}/features` и
  `gatling://examples/{language}/{protocol}/{pattern}`.
- `Prompts` с объявленными аргументами, включая `create_simulation` и
  `import_http_plan`, которые помогают LLM пройти этапы планирования, валидации
  и генерации.
- Варианты автодополнения для аргументов `prompts` и шаблонов ресурсов, включая
  `gatlingVersion`, `language`, `buildTool`, `protocol`, тип источника импорта,
  категорию и имя метода, а также шаблон примера.

### Ресурсы возможностей для протоколов, отличных от HTTP

Матрица возможностей, построенная по исходным данным, доступна по URI
`gatling://compatibility/protocol-matrix`. Для HTTP сохраняется полноценная
генерация. Следующие пять ресурсов для Java содержат предварительные требования
для каждого протокола, проверки зависимостей и версий, а также
детерминированные предупреждения в режиме `capability-only`:

- `gatling://examples/java/websocket/capability-notes`
- `gatling://examples/java/sse/capability-notes`
- `gatling://examples/java/jms/capability-notes`
- `gatling://examples/java/mqtt/capability-notes`
- `gatling://examples/java/grpc/capability-notes`

Полная генерация кода отключена для WebSocket, SSE, JMS, MQTT и gRPC. Сервер
возвращает сведения о возможностях и предупреждения, не создавая вымышленный
фрагмент исходного кода. Генерация HTTP и проверенные примеры плагинов
сообщества для JVM остаются доступны через существующие интерфейсы.

## Проверенные плагины сообщества

Генерация для плагинов включается явно и использует точные строки совместимости,
подтверждённые источниками. Текущие проверенные сочетания:

| Плагин | Версия плагина | Проверенная версия Gatling | Java | Источник |
| --- | --- | --- | --- | --- |
| Kafka | `1.0.6` | `3.13.5` | `17+` | [выпуск](https://github.com/galax-io/gatling-kafka-plugin/releases/tag/v1.0.6) |
| JDBC | `1.2.0` | `3.13.5` | `11+` | [выпуск](https://github.com/galax-io/gatling-jdbc-plugin/releases/tag/v1.2.0) |
| AMQP | `1.3.3` | `3.13.5` | `17+` | [выпуск](https://github.com/galax-io/gatling-amqp-plugin/releases/tag/v1.3.3) |
| Picatinny | `1.24.0` | `3.13.5` | `17+` | [выпуск](https://github.com/galax-io/gatling-picatinny/releases/tag/v1.24.0) |

Для Java и Kotlin используются Maven или Gradle, для Scala — sbt. Если
запрошенное сочетание не проверено, сервер возвращает
`plugin.compatibility.unverified`, `plugin.dsl.unsupported`,
`plugin.build-tool.unsupported` или `plugin.java.unsupported` и не генерирует
код. Примеры Kafka и AMQP содержат request-reply, проверки и настройки
корреляции; JDBC содержит проверку результата; Picatinny содержит feeder,
границы транзакции и assertion. Сервисы, драйверы, брокеры, топики, очереди и
специальные репозитории плагинов остаются явными требованиями проекта.

## Контекст проекта и корневые каталоги рабочей области

Инструменты для работы с файловой системой доступны только для чтения и
ограничены настроенными корневыми каталогами рабочей области. По умолчанию при
локальном запуске JAR разрешён текущий рабочий каталог. В контейнерных образах
задано `GATLING_MCP_WORKSPACE_ROOTS=/workspace`, что соответствует целевому
каталогу документированного привязного монтирования (`bind mount`).

Чтобы разрешить один или несколько корневых каталогов, перечисленных через
запятую, задайте `GATLING_MCP_WORKSPACE_ROOTS`:

```bash
GATLING_MCP_WORKSPACE_ROOTS=/absolute/path/to/project \
  java -jar target/gatling-community-mcp-0.4.0-all.jar
```

Инструменты, учитывающие контекст проекта:

- `gatling_get_project_context`: возвращает систему сборки, каталоги исходного
  кода и тестов, обнаруженную версию Gatling, версию Java или Node, файлы
  сценариев, состояние зависимостей и плагинов, имена пакетов, принятый стиль и
  предупреждения.
- `gatling_explain_project_context`: возвращает те же структурированные данные
  и краткое объяснение для LLM.
- `gatling_resolve_effective_context`: объединяет явно заданные значения с
  обнаруженным контекстом проекта и возвращает конкретные `gatlingVersion`,
  `language`, `buildTool`, `protocol` и версии среды выполнения для последующих
  вызовов инструментов.

Пути за пределами настроенных корневых каталогов отклоняются со
структурированным кодом ошибки `path.outside_workspace`.

## Сведения о методах DSL

Используйте эти инструменты, когда LLM нужны точные сведения о Gatling DSL без
угадывания имён методов по памяти:

- `gatling_list_dsl_methods`: возвращает строки методов с фильтрацией по
  `gatlingVersion`, `language`, `protocol` и необязательной `category`.
- `gatling_explain_dsl_method`: возвращает категорию, ограничения по версиям,
  синтаксис для конкретного DSL, официальный URL или URL источника, уровень
  достоверности, родительский контекст, обязательный предыдущий метод,
  несовместимые методы и замечания о риске ошибки компиляции.
- `gatling_validate_method_chain`: валидирует упорядоченную цепочку методов,
  например `Simulation -> http -> baseUrl -> scenario -> exec -> http -> post
  -> check -> jsonPath.saveAs`.
- `gatling_find_replacement_method`: сопоставляет известные переименованные или
  удалённые методы, например `heavisideUsers`, с `stressPeakUsers` для активных
  диапазонов версий Gatling.

Валидатор цепочек методов служит семантическим ограничителем. До генерации он
проверяет доступность версии и DSL, обязательные предыдущие методы и
несовместимые комбинации. Семантическая матрица формируется из исходных данных
и включает синтаксис для конкретного DSL, замечания о риске ошибки компиляции и
правила, например выбор между `body.StringBody` и `formParam`, требование
HTTP-конструктора запроса для `check` и требование `check` для
`jsonPath.saveAs`.
Итоговую корректность исходного кода по-прежнему следует проверять через
`gatling_compile_check`.

Пример ресурса:

```text
gatling://examples/java/http/login-token-orders
```

## Расширенная модель HTTP-плана

Генерация HTTP начинается с плана. До создания кода инструменты формируют и
валидируют структурированный `HttpSimulationPlan`.

К основным полям плана относятся:

- `steps` сценария: запросы, получение данных (`feed`), паузы, группы, циклы
  `repeat`/`during`/`forever` и условные ветви.
- параметры запроса: заголовки, параметры строки запроса и формы, части
  `multipart body`, ресурсы, проверки, аутентификация, cookies, перенаправления,
  скрытые запросы и тела запросов.
- параметры протокола: базовый URL, заголовки уровня протокола, HTTP/2, политика
  перенаправлений и настройки proxy.
- источники данных (`feeders`), профили нагрузки и глобальные проверки.

Валидация, диагностика, требования к методам, решения о генерации и генераторы
кода используют одну модель обхода, поэтому вложенные запросы не остаются
незамеченными.

## Паритет генераторов кода

`gatling_generate_from_plan` создаёт код из расширенных HTTP-планов для Java,
Kotlin, Scala, JavaScript и TypeScript. Эталонные тесты проверяют одинаковую
поддержку источников данных (`feeders`), групп, пауз, циклов, условных ветвей,
полей `query`/`form`/`multipart`, ресурсов, аутентификации, cookies,
перенаправлений, HTTP/2 и proxy.

Генераторы намеренно консервативны. Они создают код для локальной разработки и
проверки компиляции, но не запускают нагрузочные тесты.

## Проверка сгенерированного кода

`gatling_validate_generated_code` выполняет детерминированные статические
проверки перед проверкой компиляции проекта:

- проверяет язык и операторы импорта для Java, Kotlin, Scala, JavaScript и
  TypeScript.
- проверяет обязательную структуру `Simulation`/`setUp`.
- выявляет пропущенные маркеры расширенных возможностей.
- выявляет отсутствующие проверки запросов.
- выявляет небезопасные условные заполнители, например всегда истинный
  `doIf`.
- выявляет корреляционные ссылки с `#{var}`, для которых не найден более
  ранний `saveAs`.

Инструмент возвращает `compileReadiness` и рекомендуемую команду компиляции для
выбранной системы сборки. Это не полноценный компилятор; для проверки
компиляции в изолированной копии проекта используйте `gatling_compile_check`.

## Проверка компиляции

`gatling_compile_check` только проверяет компиляцию. Он никогда не запускает
нагрузочный тест и не принимает от вызывающей стороны произвольную команду
оболочки.

Разрешённые команды:

- Maven: `mvn -q -DskipTests test-compile` или `./mvnw` из проекта.
- Gradle: `gradle testClasses` или `./gradlew` из проекта.
- sbt: `sbt Test/compile`.
- npm: `npm exec tsc -- --noEmit` при наличии `tsconfig.json`, иначе
  `npm run build --if-present`.

Инструмент копирует проект во временный каталог, исключает тяжёлые и
генерируемые каталоги, включая `.git`, `target`, `build`, `.gradle` и
`node_modules`, а затем запускает там команду компиляции. Укажите
`"execute": false`, чтобы получить запланированную команду без её выполнения.

Опубликованный контейнерный образ включает Java 25 и Maven, поэтому Maven
`test-compile` работает без скрипта-обёртки из проекта. Для проверок Gradle,
sbt и npm нужен соответствующий скрипт-обёртка в проекте или собственный образ
с этими инструментами.

## Импорт HTTP

Инструменты импорта не записывают файлы и не генерируют код напрямую. Они
преобразуют внешние описания HTTP-взаимодействий в структурированный план
сценария, валидируют его и возвращают предупреждения до генерации.

- `gatling_import_openapi`: OpenAPI JSON или YAML, включая документы
  Swagger/OpenAPI 2.0 и OpenAPI 3.x.
- `gatling_import_har`: записи HAR JSON, включая HAR 1.1 и актуальную 1.2.
- `gatling_import_curl`: одна команда curl, включая классический синтаксис
  `-d`/`--data-*` и современный `--json`.
- `gatling_import_postman_collection`: Postman Collection v2.0 и v2.1 JSON.

Когда `valid=true`, результат импорта можно передать в
`gatling_generate_from_plan`. Сервер автоматически определяет версию источника
и возвращает её в `sourceVersion`. Чувствительные заголовки, включая
`Authorization`, `Cookie` и `x-api-key`, маскируются в возвращаемом плане.

### Локальные ссылки OpenAPI

Импорт OpenAPI разрешает только локальные ссылки JSON Pointer, которые
начинаются с `#/components/...`. Разрешение ссылок ограничено значением
`depth 8` и выполняется в новых внутренних отображениях и списках, поэтому
разобранный исходный документ не изменяется. Параметры операций и элементов
пути, тела запросов и схемы, содержимое и примеры ответов, а также схемы
безопасности используют разрешённые локальные ссылки там, где это необходимо.
Путь вида
`/orders/{orderId}` превращается в `/orders/#{orderId}` и также становится
кандидатом для источника данных (`feeder`).

Инструмент импорта не выполняет сетевые запросы. Он возвращает
детерминированные предупреждения: внешние ссылки дают
`import.openapi.remote_ref_unsupported`; неподдерживаемые локальные пути дают
`import.openapi.ref_unsupported`; циклические локальные ссылки дают
`import.openapi.ref_circular`; неразрешённые локальные компоненты дают
`import.openapi.ref_unresolved`; цепочки длиннее лимита дают
`import.openapi.ref_depth_exceeded`. Конструкции клиентского потока `callbacks`,
`webhooks` и `response links` остаются неподдерживаемыми и возвращают
соответствующие предупреждения.

## Экспертная демонстрация

Используйте этот раздел для локальной демонстрации, готовой к экспертной проверке. Она охватывает
определение контекста проекта, импорт OpenAPI и curl, валидацию плана и цепочки
методов,
генерацию кода, проверку компиляции и анализ отчётов и журналов с примерами из
`examples/openapi/orders-api.yaml` и `examples/curl/login-orders.sh`.

## Анализ отчётов и журналов

Инструменты анализа отчётов и журналов доступны только для чтения. Они не
запускают Gatling, не изменяют отчёты и не записывают файлы.

- `gatling_analyze_report`: принимает `reportPath` или `reportContent`,
  распознаёт Gatling `stats.js` и `stats.json` и возвращает `globalStats`,
  `requestStats`, `topSlowRequests`, `topFailedRequests`, `findings` и
  `warnings`.
- `gatling_analyze_log`: принимает `logPath` или `logText`, распознаёт журналы
  выполнения и, насколько позволяет формат, события запросов из
  `simulation.log`. Он возвращает сведения о таких проблемах, как отказ в
  соединении, тайм-аут, ошибка согласования TLS, ошибка DNS, исчерпание памяти
  JVM и исчерпание источника данных (`feeder`).

При наличии HTML-отчёта Gatling используйте `gatling_analyze_report`. Разбор
сырого `simulation.log` намеренно выполняется лишь настолько точно, насколько
позволяет формат, поскольку этот файл не является стабильным публичным
форматом интеграции.

Пример:

```json
{
  "reportPath": "/absolute/path/to/target/gatling/orderssimulation-20260704080000000",
  "contentType": "AUTO",
  "errorRateThreshold": 1.0,
  "p95ThresholdMs": 1000,
  "p99ThresholdMs": 2000
}
```

## Целевая диагностика

Используйте специализированные инструменты диагностики, когда LLM требуется
конкретное следующее действие, а не общая сводка по отчёту:

- `gatling_explain_errors`: преобразует ошибки выполнения Gatling, неуспешные
  проверки и трассировки стека в `findings`, гипотезы о первопричине, шаги
  проверки и следующие действия. Если известный шаблон не найден, инструмент возвращает
  `errors.no_known_patterns` с низкой достоверностью и не придумывает гипотезы
  о первопричине.
- `gatling_suggest_load_model`: предлагает исходную открытую или закрытую
  модель нагрузки на основе целевого RPS, ожидаемого числа пользователей,
  продолжительности, разгона и задержки p95.
- `gatling_check_feeder_risk`: проверяет неопределённые источники данных
  (`feeders`), конечные источники в режимах `queue`/`shuffle`, риск нехватки
  строк и подстановочные значения сессии без соответствующих данных feeder.
- `gatling_check_correlation_risk`: сверяет ссылки `#{session}` с более ранней
  корреляцией через `check(...saveAs(...))` и распространёнными значениями из
  источника данных (`feeder`).
- `gatling_check_assertion_quality`: проверяет отсутствующие проверки статуса,
  `assertions` для доли ошибок, ограничения задержки и слабые критерии качества.

Все пять инструментов возвращают единую структурированную RCA-модель:
`findings`, `rootCauseHypotheses`, `verificationSteps`, `nextActions`,
`confidence`, `assumptions` и `risks`.

Проверки источников данных, корреляции и `assertions` используют расширенный
обход HTTP-шагов, поэтому анализируют запросы, вложенные в группы, циклы и
условные ветви.

## Возможности взаимодействия MCP

Возможности взаимодействия P4 необязательны и корректно отключаются, если
клиент их не поддерживает.

- `gatling_interaction_status`: сообщает о поддержке клиентом `elicitation`,
  `sampling` и уведомлений о ходе выполнения.
- `Progress`: инструменты отправляют уведомления MCP о ходе выполнения, только
  когда входящий запрос содержит `_meta.progressToken`.
- `Elicitation`: `gatling_plan_simulation` может запросить у клиента недостающие
  `goal`, `baseUrl` и `simulationClassName`, когда `useElicitation=true`.
- `Sampling`: `gatling_plan_simulation`, `gatling_analyze_report` и
  `gatling_analyze_log` могут запросить у модели клиента рекомендательную
  проверку, когда `useSampling=true`.

Результат `sampling` носит рекомендательный характер. Детерминированные
структурированные поля, включая `plan`, `findings`, `requestStats` и `warnings`,
остаются основным результатом MCP.

## Безопасность и наблюдаемость

- Все пути файловой системы разрешаются внутри настроенных корневых каталогов
  рабочей области. Пути за их пределами возвращают структурированные ошибки
  инструмента `path.outside_workspace`.
- Каждый публичный инструмент защищён рекурсивными лимитами на глубину, число
  узлов, общий размер текста и размер одной строки. Слишком большой `input`
  возвращает структурированные ошибки `input.*` до передачи на выполнение.
- События аудита жизненного цикла инструмента выводятся как строки JSON в
  `stderr`: `tool.started`, `tool.finished` и `tool.failed`. События содержат
  имя инструмента, ключи аргументов, приблизительный размер текста, размер
  результата и стабильный код ошибки. Они не содержат исходные тела запросов,
  сгенерированный код, данные источника (`feeder`) или исходные значения
  заголовков.
- Секреты в журналах, `prompts` для `sampling`, диагностических данных и ошибках
  маскируются до возврата или записи в журнал. Чувствительные значения
  `Authorization` возвращаются с замаскированными значениями токенов.

## Режимы запуска

По умолчанию используется `stdio`. Это основной вариант, когда MCP-клиент
может запустить локальный процесс или контейнер.

```bash
java -jar target/gatling-community-mcp-0.4.0-all.jar
```

Режим HTTP-сервера предназначен для пользователей, которым нужно один раз
запустить MCP-сервер в фоновом режиме и подключать инструменты к
`http://127.0.0.1:8765/mcp`.

```bash
java -jar target/gatling-community-mcp-0.4.0-all.jar \
  http --bind 127.0.0.1 --port 8765
```

Режим bridge предназначен для MCP-клиентов, которые поддерживают только
`command` и `args`, когда требуется подключиться к уже запущенному
HTTP-серверу.

```bash
java -jar target/gatling-community-mcp-0.4.0-all.jar \
  bridge --url http://127.0.0.1:8765/mcp
```

HTTP-режим рассчитан прежде всего на локальную работу. Оставляйте его на
loopback (`127.0.0.1`) или публикуйте порт контейнера только на loopback.

## Сборка из исходного кода

На macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"

mvn test
mvn -q -DskipTests package
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- "$JAVA_HOME/bin/java" -jar target/gatling-community-mcp-0.4.0-all.jar
```

На Linux и других Unix-подобных системах должен быть заранее установлен
JDK 25, а `JAVA_HOME` должен указывать на него:

```bash
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

mvn test
mvn -q -DskipTests package
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- "$JAVA_HOME/bin/java" -jar target/gatling-community-mcp-0.4.0-all.jar
```

Ожидаемый результат: тесты Maven и сборка пакета завершаются успешно, а скрипт
smoke-теста заканчивает работу строкой `MCP smoke passed`.

Образы выпусков публикуются в GitHub Container Registry с тегом `latest`,
тегами ветки версий, например `0.4`, и точными тегами выпусков, например
`0.4.0`. При выпуске на GitHub также автоматически обновляется зеркало Docker
Hub.

## Хранение тегов Docker Hub

Зеркало Docker Hub хранит три тега текущего стабильного выпуска:

- `0.4.0`: точный тег выпуска для воспроизводимых установок.
- `0.4`: изменяемый тег ветки для последнего выпуска `0.4.x`.
- `latest`: изменяемый тег стабильной версии для быстрой локальной настройки.

Не используйте только `latest` в сохранённых конфигурациях MCP-клиента. Если
важна воспроизводимость, указывайте точный тег выпуска.

После публикации и smoke-теста образа процесс выпуска применяет политику
хранения тегов Docker Hub через `.github/workflows/dockerhub-retention.yml`.
Для ранних выпусков до версии 1.0 старые теги зеркала Docker Hub удаляются
вместе со старыми дайджестами манифестов, на которые больше нет ссылок. Когда у
проекта появятся внешние пользователи, зависящие от точных тегов, увеличьте
период хранения и не удаляйте старые стабильные теги сразу.

Текущий выпуск поддерживает несколько архитектур. Сохраняйте манифесты
`linux/amd64` и `linux/arm64`, связанные с `0.4.0`, `0.4` и `latest`.

## Подключение через stdio

Используйте этот режим, когда MCP-клиент может запускать процесс. Клиент
отправляет сообщения JSON-RPC в `stdin` и читает ответы MCP из `stdout`.

Важные правила для контейнера:

- Указывайте абсолютный путь на хосте в привязном монтировании (`bind mount`).
  Большинство MCP-клиентов не выполняют подстановку `$PWD` оболочкой внутри
  конфигурации JSON.
- Сохраняйте `-i`: для транспорта stdio требуется открытый `stdin`.
- Не используйте `-d`: MCP-клиент должен владеть каналами дочернего процесса.
- Оставляйте проект смонтированным только для чтения, пока будущему инструменту
  явно не потребуется доступ на запись.

Конфигурация MCP-клиента для Docker:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "docker",
      "args": [
        "run",
        "--rm",
        "-i",
        "--name",
        "mcp-gatling-community",
        "--label",
        "org.opencontainers.image.title=MCP Gatling-community",
        "--env",
        "GATLING_MCP_WORKSPACE_ROOTS=/workspace",
        "--mount",
        "type=bind,source=/absolute/path/to/project,target=/workspace,readonly",
        "ghcr.io/ravabl/gatling-community-mcp:latest"
      ]
    }
  }
}
```

Конфигурация MCP-клиента для Apple Containers:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "container",
      "args": [
        "run",
        "--rm",
        "-i",
        "--name",
        "mcp-gatling-community",
        "--label",
        "org.opencontainers.image.title=MCP Gatling-community",
        "--env",
        "GATLING_MCP_WORKSPACE_ROOTS=/workspace",
        "--mount",
        "type=bind,source=/absolute/path/to/project,target=/workspace,readonly",
        "ghcr.io/ravabl/gatling-community-mcp:latest"
      ]
    }
  }
}
```

Конфигурация MCP-клиента для локального JAR:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/gatling-community-mcp-0.4.0-all.jar"],
      "env": {
        "GATLING_MCP_WORKSPACE_ROOTS": "/absolute/path/to/project"
      }
    }
  }
}
```

Сервер записывает сообщения MCP JSON-RPC только в `stdout`. Журналы
направляются в `stderr`.

## Запуск локального HTTP-сервера

Используйте режим HTTP-сервера, когда требуется запустить сервер вручную и
оставить его работать в фоновом режиме.

Docker:

```bash
docker run --rm -d \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --publish 127.0.0.1:8765:8765 \
  --mount "type=bind,source=/absolute/path/to/project,target=/workspace,readonly" \
  ghcr.io/ravabl/gatling-community-mcp:latest \
  http --bind 0.0.0.0 --port 8765
```

Apple Containers:

```bash
container run --rm --detach \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --publish 127.0.0.1:8765:8765 \
  --mount "type=bind,source=/absolute/path/to/project,target=/workspace,readonly" \
  ghcr.io/ravabl/gatling-community-mcp:latest \
  http --bind 0.0.0.0 --port 8765
```

Проверка и остановка сервера:

```bash
curl -fsS http://127.0.0.1:8765/healthz
docker logs -f mcp-gatling-community
docker stop mcp-gatling-community
```

Прямая конфигурация HTTP MCP-клиента для клиентов с поддержкой MCP HTTP:

```json
{
  "mcpServers": {
    "gatling-community": {
      "url": "http://127.0.0.1:8765/mcp"
    }
  }
}
```

## Подключение к HTTP-серверу через bridge

Используйте режим bridge, когда HTTP-сервер уже запущен, а MCP-клиент принимает
только локальную команду.

Bridge через локальный JAR:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/target/gatling-community-mcp-0.4.0-all.jar",
        "bridge",
        "--url",
        "http://127.0.0.1:8765/mcp"
      ]
    }
  }
}
```

Bridge через контейнер:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "docker",
      "args": [
        "run",
        "--rm",
        "-i",
        "ghcr.io/ravabl/gatling-community-mcp:latest",
        "bridge",
        "--url",
        "http://host.docker.internal:8765/mcp"
      ]
    }
  }
}
```

При использовании Docker в Linux добавьте
`"--add-host", "host.docker.internal:host-gateway"` в аргументы контейнера
bridge, если `host.docker.internal` недоступен.

## Поведение stdio при ручном запуске

При запуске stdio на переднем плане сервер ожидает, что MCP-клиент или скрипт
smoke-теста будет отправлять сообщения JSON-RPC. Если запустить сервер вручную
и закрыть `stdin`, он может записать ошибку разбора EOF, например:

```text
MismatchedInputException: No content to map due to end-of-input
```

Это означает, что транспорт MCP попытался прочитать следующее сообщение
JSON-RPC, но входной поток завершился. Для длительной ручной работы используйте
режим HTTP-сервера.

Для проверки протокола используйте smoke-тест:

```bash
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- java -jar target/gatling-community-mcp-0.4.0-all.jar
```

Для проверки запущенного HTTP-сервера через bridge:

```bash
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- java -jar target/gatling-community-mcp-0.4.0-all.jar \
  bridge --url http://127.0.0.1:8765/mcp
```

## Развёртывание в контейнере

Используйте эти команды, чтобы перед публикацией выпуска проверить тот же режим
stdio на переднем плане с локально собранным образом.

### Проверка контейнерных сред выполнения

Проверка в условиях, близких к выпуску, была выполнена локально с тегом
`gatling-community-mcp:hardening`: Docker и Apple Containers собрали образ и
успешно выполнили smoke-тест stdio. Для проверки Apple Containers должны быть
доступны системная служба и служба сборки. Тег `hardening` предназначен только
для локальной проверки; для развёртывания используйте тег выпуска.

Сборка и smoke-тест с Docker:

```bash
docker build -t gatling-community-mcp:local .

./scripts/mcp-smoke.py \
  --project-path /workspace \
  -- docker run --rm -i \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --mount "type=bind,source=$PWD,target=/workspace,readonly" \
  gatling-community-mcp:local
```

Сборка и smoke-тест с Apple Containers:

```bash
container system start
container builder start
container build -f Dockerfile -t gatling-community-mcp:local .

./scripts/mcp-smoke.py \
  --project-path /workspace \
  -- container run --rm -i \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --mount "type=bind,source=$PWD,target=/workspace,readonly" \
  gatling-community-mcp:local
```

Имя контейнера — `mcp-gatling-community`. OCI-метка `title` — `MCP Gatling-community`.

## Участие в разработке

Используйте Java 25 и Maven. Делайте небольшие изменения, добавляйте или
обновляйте тесты и запускайте:

```bash
mvn test
mvn package
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- java -jar target/gatling-community-mcp-0.4.0-all.jar
```

Перед изменением совместимости, добавлением нового плагина или изменением
поведения протокола сначала создайте issue. Небольшие исправления документации
можно сразу отправлять как pull request. В pull request укажите, что и почему
изменилось, связанную issue при наличии и точные команды успешной проверки.

## Работа с GitHub Issues

Выберите категорию GitHub issue, соответствующую задаче:

- Отчёт об ошибке: что-либо работает неправильно или небезопасно.
- Запрос новой возможности: новое поведение MCP или новый процесс работы.
- Документация: документация непонятна или неполна.
- Совместимость: поддержка версии Gatling, DSL, системы сборки или протокола.
- Плагин сообщества: Kafka, JDBC, AMQP, Picatinny или другой плагин JVM.
- Вопрос: помощь по использованию, которая ещё не является ошибкой или запросом
  функции.

Шаблоны запрашивают минимальный контекст, необходимый для воспроизведения или
принятия решения. Создание GitHub issues без шаблона отключено, чтобы список
задач оставался читаемым.

## Обновление исходных данных

Утверждения о совместимости хранятся в `src/main/resources/data`, а не внутри
prompts. Изменение исходных данных должно содержать ссылку на первичную
документацию или выпуск, явный уровень достоверности, ограничения по версиям и
DSL, semantic rules для каждого добавленного метода, а также целевые
положительные и отрицательные тесты. Генерация для плагина включается только для
точного проверенного сочетания. Перед отправкой изменения запустите полный набор
тестов на Java 25 и MCP smoke. Maven проверяет JaCoCo gates: не менее 85%
покрытия строк и 60% покрытия ветвей.

## Примечания к выпуску

### 0.4.0

- Добавлен расширенный анализ существующих симуляций и chain-aware валидация
  использования возможностей.
- Базовые authoring tools возвращают строгие структурированные данные о target,
  warnings, findings, capabilities и plugin identity.
- Для Kafka, JDBC, AMQP и Picatinny используются точные проверенные сочетания и
  независимые примеры на Java, Kotlin и Scala.
- Добавлены полные resources с рецептами и шаблонами проектов, а source-backed
  каталог HTTP DSL расширен до 132 методов.
- Поиск контекста проекта стал ограниченным и быстрым в Docker и Apple
  Containers с файловой системой только для чтения: сервер сканирует
  обнаруженные source roots и без комбинаторной рекурсии разрешает свойства
  Maven.
- Публичная документация объединена в английский и русский README и обзор
  Docker Hub с Quick Start для Docker и standalone-запуска.

## Лицензия

Проект распространяется по лицензии Apache License 2.0. См. `LICENSE`.
