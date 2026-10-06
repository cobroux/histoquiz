# HistoQuiz

Quiz d'histoire multijoueur en temps réel, de la Première Guerre mondiale à la fin de la guerre froide (1914-1991).

- **Solo** : une partie rapide, à ton rythme.
- **Salon** : crée un salon, partage le code (ou le lien), et jouez en même temps. Les réponses rapides rapportent plus de points.
- Interface pensée pour le **mobile** (gros boutons tactiles, mode sombre automatique, reconnexion automatique).

## Stack

| Partie | Techno |
| --- | --- |
| `backend/` | Java 21, Spring Boot 4.1, WebSocket STOMP |
| `frontend/` | Angular 22 (standalone, signals, zoneless), `@stomp/stompjs` |

Le serveur fait autorité : il garde l'état des parties en mémoire, gère les chronos et diffuse un instantané du salon à chaque changement sur `/topic/rooms/{code}`. La bonne réponse n'est jamais envoyée avant la fin de la question.

## Lancer en développement

Prérequis : JDK 21, Maven, Node.js ≥ 22.22.3 (ou 24).

```bash
# Terminal 1 — API sur http://localhost:8080
cd backend
mvn spring-boot:run

# Terminal 2 — front sur http://localhost:4200 (proxy vers l'API, voir proxy.conf.json)
cd frontend
npm install
npm start
```

Pour tester le multijoueur depuis ton téléphone sur le même Wi-Fi : `npm start -- --host 0.0.0.0`, puis ouvre `http://<ip-de-ton-pc>:4200`.

Tests backend : `cd backend && mvn test`.

## Lancer avec Docker

```bash
docker compose up --build
```

L'application est servie sur http://localhost:8000 (nginx sert le front et relaie `/api` et `/ws` vers le backend).

## Fonctionnement

### REST

| Méthode | Route | Rôle |
| --- | --- | --- |
| `GET` | `/api/periods` | Périodes disponibles et nombre de questions |
| `POST` | `/api/rooms` | Crée un salon (`{name, periods, questionCount, secondsPerQuestion, solo}`) |
| `POST` | `/api/rooms/{code}/players` | Rejoint un salon (`{name}`) |
| `GET` | `/api/rooms/{code}` | État courant du salon |

La création et l'arrivée dans un salon renvoient `{code, playerId, token}`. Le `token` est secret et sert à authentifier les actions du joueur. Le front le garde dans le `localStorage`, ce qui permet de recharger la page sans perdre sa place.

### WebSocket (STOMP sur `/ws`)

- Abonnement : `/topic/rooms/{code}` (instantané `RoomView` à chaque changement).
- Actions (`{token, ...}`) : `/app/rooms/{code}/hello`, `start`, `answer` (`questionIndex`, `choice`), `next`, `restart`, `leave`.

Déroulement : `LOBBY` → `QUESTION` → `REVEAL` → … → `FINISHED`. Une question se ferme quand tous les joueurs connectés ont répondu ou à la fin du chrono. En multijoueur, la question suivante arrive après 8 s (l'hôte peut passer). En solo, le joueur avance quand il veut.

Score : 500 points par bonne réponse, plus jusqu'à 500 points selon la rapidité.

## Ajouter des questions

Tout est dans [`backend/src/main/resources/questions.json`](backend/src/main/resources/questions.json). **Le premier choix est toujours la bonne réponse** : l'ordre des choix est mélangé à chaque partie.

```json
{
  "period": "GUERRE_FROIDE",
  "question": "En quelle année est construit le mur de Berlin ?",
  "choices": ["1961", "1953", "1968", "1989"],
  "explanation": "Érigé dans la nuit du 12 au 13 août 1961 pour stopper l'exode vers l'Ouest."
}
```

Périodes possibles : `PREMIERE_GUERRE`, `ENTRE_DEUX_GUERRES`, `SECONDE_GUERRE`, `GUERRE_FROIDE`.
