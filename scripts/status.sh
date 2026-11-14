#!/usr/bin/env bash
# Muestra qué versión corre en cada ambiente.
for env in uat prod; do
  port=$([ "$env" = uat ] && echo 8081 || echo 8082)
  printf "%-5s " "$env"
  curl -fsS "http://localhost:${port}/api/info" 2>/dev/null || printf "(no desplegado)"
  echo
done
