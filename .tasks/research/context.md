# Контекст бага

## Симптом
При подключении к HTTP streamable MCP-серверу (URL: https://docs.langchain.com/mcp)
падает с ошибкой:
`io.modelcontextprotocol.spec.McpTransportException: Invalid SSE response. Status code: 200 Line: {`

## Причина
GET "listening" SSE-стрим в `HttpClientStreamableHttpTransport.reconnect()` БЕЗУСЛОВНО
парсит тело ответа как SSE (через `ResponseSubscribers.sseToBodySubscriber`), не проверяя
`Content-Type`. Сервер docs.langchain.com на `GET /mcp` (открытие listening-стрима) отдаёт
кешированный (Cloudflare/Vercel) ответ `200 OK` с `Content-Type: application/json` и
pretty-printed JSON-телом, начинающимся со строки `{`. SSE-парсер
(`ResponseSubscribers$SseLineSubscriber.hookOnNext`) встречает строку `{`, которая не
является валидным SSE-полем, и бросает `Invalid SSE response`.

В отличие от GET-ветки, POST-ветка (`toSendMessageBodySubscriber`) выбирает парсер по
`Content-Type` корректно.

## Текущее состояние фикса
Фикс находится в `ResponseSubscribers.SseLineSubscriber.hookOnNext` (НЕ в transport-файле):
в блоке `else` (строка не является SSE-полем) условие пропуска тела расширено с `== 405`
до `statusCode == 405 || successfulNonSse`, где `successfulNonSse` = (2xx статус И
Content-Type НЕ содержит text/event-stream). Это позволяет тихо игнорировать тело при
успешном не-SSE ответе на GET-стрим.

История:
- commit ca88d29 "correct tests" сузил условие с `>= 400` до `== 405`.
- поверх него (незакоммичено) добавлена ветка `successfulNonSse`.
- добавлен новый тест mcp-test/.../HttpClientStreamableHttpTransportGetJsonResponseTest.java

## Вопрос для исследования
Действительно ли этот кейс (GET/listening SSE-стрим возвращает успешный 2xx ответ с
не-SSE Content-Type, напр. application/json) НЕ покрыт существующими тестами в репозитории
ДО добавления нового теста?
