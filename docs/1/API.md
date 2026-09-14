# SnapSpend API

Base URL local: `http://localhost:5080/api`

Auth: JWT Bearer.

## Auth

### POST /auth/register

```json
{"email":"a@b.com","username":"alice","password":"12345678"}
```

### POST /auth/login

```json
{"email":"a@b.com","password":"12345678"}
```

## Expenses

### GET /expenses

Returns the current user's expenses.

### POST /expenses

Multipart form:

- `amount`: integer VNĐ
- `category`: category key; use `auto` to accept AI classification
- `note`: optional text
- `expenseDate`: `YYYY-MM-DD`
- `image`: optional image file

### PUT /expenses/{id}

```json
{
  "amount": 199000,
  "category": "food",
  "note": "Dinner",
  "expenseDate": "2026-09-03"
}
```

### DELETE /expenses/{id}

Deletes the current user's expense.

### POST /expenses/{id}/share/{friendId}

Shares an expense with an accepted friend.

## Stats

### GET /stats?from=YYYY-MM-DD&to=YYYY-MM-DD

Returns:

- total
- averageDaily
- byCategory
- byDay

### POST /ai/analyze?from=YYYY-MM-DD&to=YYYY-MM-DD

Builds an aggregate text summary and asks AI for Vietnamese analysis.

## Friends

### GET /friends

Returns accepted friends.

### POST /friends

```json
{"username":"bob"}
```

For this MVP, adding a friend sets the relationship to `accepted` immediately. Production should implement request/accept/reject states.

## Account

### DELETE /account

Deletes user and cascaded user-owned data.
