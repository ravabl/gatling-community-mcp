#!/usr/bin/env bash
set -euo pipefail

curl -X POST 'https://api.example.test/login' \
  -H 'Content-Type: application/json' \
  --data-raw '{"username":"{{username}}","password":"{{password}}"}'

curl -X GET 'https://api.example.test/orders/{{userId}}' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer {{token}}'
