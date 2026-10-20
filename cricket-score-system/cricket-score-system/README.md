# Cricket Score System (Spring Boot)

Run: `mvn spring-boot:run` (Java 17+), then open http://localhost:8080

| Method | Endpoint | Purpose |
|---|---|---|
| GET | /api/matches | All matches with full scorecards |
| GET | /api/matches/{id} | One match |
| GET | /api/matches/{id}/players | Batting and bowling stats per innings |
| GET | /api/matches/{id}/commentary | Ball-by-ball commentary (latest first) |
| GET | /api/matches/{id}/summary | Score, run rate, required rate, top performers, result |
| POST | /api/matches | `{"home":"India","away":"England","overs":20,"venue":"Eden Gardens"}` |
| POST | /api/matches/{id}/ball | Manually bowl the next ball |
| GET | /api/stream | Server-Sent Events: `match` event on every ball |

Teams: India, Australia, England, South Africa. Ball speed: `cricket.tick-ms` in application.properties.
