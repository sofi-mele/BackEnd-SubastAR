#!/bin/bash
ENV_FILE="src/main/resources/BackEnd-SubastAR.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "No se encontro $ENV_FILE"
  exit 1
fi

while IFS='=' read -r key rest; do
  key="${key%$'\r'}"
  rest="${rest%$'\r'}"
  [[ -z "$key" || "$key" == \#* ]] && continue
  value="${rest%\"}"
  value="${value#\"}"
  export "$key=$value"
done < "$ENV_FILE"

echo "DB_URL cargada: ${DB_URL:+SI (${#DB_URL} chars)}"
echo "DB_USERNAME: ${DB_USERNAME:-NO CARGADA}"

mvn spring-boot:run
