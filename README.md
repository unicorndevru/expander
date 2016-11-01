# Expander

[![Build Status](https://travis-ci.org/unicorndevru/expander.svg?branch=master)](https://travis-ci.org/unicorndevru/expander)

[![](https://jitpack.io/v/unicorndevru/expander.svg)](https://jitpack.io/#unicorndevru/expander)


A tool to build composed JSON responses fetching several destinations.
Expander is to be compared with `GraphQL`, but offers less with the smaller effort: Expander is config-based and extremely simple to set up and use.

Build with _Scala_ and _akka-http_, but can also be used as a proxy.

## Example:

`GET /api/auth`

```json
{
  "userId": "123",
  "roles": ["admin"]
}
```

With expander can be auto-composed with the fetched User resource:

`GET /api/auth?_expand=user`

```json
{
  "userId": "123",
  "roles": ["admin"],
  "user": {
    "id": "123",
    "name": "Fred"
  }
}
```

This requires the following configuration to be provided:

```hocon
expander {
  base-url: "http://localhost:9000/api"
  patterns: [
    {
      url: "/users/:userId"
      path: user
    }
  ]
}
```

Expander does idempotent `GET` requests, forwarding or setting headers, and caches responses within a session.

It also could expand arrays of values:

```json
{
  "userIds": ["1", "2"]
}
```

`GET /api/users?_expand=users`

```json
{
  "userIds": ["1", "2"],
  "users": [
    {
      "id": "1",
      "name": "Alf"
    },
    {
      "id": "2",
      "name": "Bob"
    }
  ]
}
```

Or inside arrays:

```json
{
  "values": [
    {
      "id": "valId",
      "userId": "1",
      "resourceId": "res-id"
    }
  ]
}
```

`GET /api/values?_expand=values*user,values*resource`

Or to fetch all `resources` and only the first `user`:

`GET /api/values?_expand=values{[0].user,*resource}`

Response will be something like:

```json
{
  "values": [
    {
      "id": "valId",
      "userId": "1",
      "resourceId": "res-id",
      "user": {
        "id": "1"
        //, ...
      },
      "resource": {
        "id": "res-id"
        //, ...
      }
    }
  ]
}
```

You can use several fields to resolve a single resource:

```hocon
expander {
  base-url: "http://localhost:9000/api"
  forward-headers: [authorization, accept-language]
  set-headers {
    accept: application/json
  }
  patterns: [
    {
      url: "/resources/:resourceId/:userId?filter=:id"
      path: "resource.subpath"
      required {
        "id": "some.jspath.inside.object.to.id"
      }
      optional {
        "queryStringParam": "jspath.to.queryString"
      }
    }
  ]
}
```