#!/bin/sh
# Idempotent wms-realm bootstrap for Docker dev.
# 1) Creates wms-realm if missing (Admin API import).
# 2) Re-imports if realm/clients/scopes are stale.
# 3) Ensures wms-ui + wms-api have Keycloak 26 "basic" scope (JWT sub claim).
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://wms-keycloak:8080}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"
REALM_FILE="${REALM_FILE:-/import/wms-realm.json}"
EXPECTED_CLIENT="${WMS_KEYCLOAK_UI_CLIENT_ID:-wms-ui}"
API_CLIENT="${WMS_KEYCLOAK_API_CLIENT_ID:-wms-api}"
REQUIRED_SCOPE="${WMS_KEYCLOAK_REQUIRED_SCOPE:-wms-basic}"
DEFAULT_SCOPES="wms-basic wms-roles wms-claims"

echo "[wms-keycloak-init] Waiting for Keycloak admin API..."

for i in $(seq 1 60); do
  if curl -sf "${KEYCLOAK_URL}/realms/master" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -sf "${KEYCLOAK_URL}/realms/master" >/dev/null 2>&1; then
  echo "[wms-keycloak-init] ERROR: Keycloak did not become reachable"
  exit 1
fi

fetch_admin_token() {
  TOKEN_RESPONSE=$(curl -sf -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=${ADMIN_USER}" \
    -d "password=${ADMIN_PASS}" \
    -d "grant_type=password" \
    -d "client_id=admin-cli") || return 1

  printf '%s' "$TOKEN_RESPONSE" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

lookup_client_uuid() {
  CLIENT_NAME="$1"
  HTTP_CODE=$(curl -s -o /tmp/wms-client-lookup.txt -w "%{http_code}" \
    "${KEYCLOAK_URL}/admin/realms/wms-realm/clients?clientId=${CLIENT_NAME}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  if [ "$HTTP_CODE" != "200" ]; then
    return 1
  fi

  sed 's/{"id"/\
{"id"/g' /tmp/wms-client-lookup.txt \
    | grep "\"clientId\":\"${CLIENT_NAME}\"" \
    | head -n 1 \
    | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'
}

lookup_scope_uuid() {
  SCOPE_NAME="$1"
  HTTP_CODE=$(curl -s -o /tmp/wms-scope-lookup.txt -w "%{http_code}" \
    "${KEYCLOAK_URL}/admin/realms/wms-realm/client-scopes" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  if [ "$HTTP_CODE" != "200" ]; then
    return 1
  fi

  sed 's/{"id"/\
{"id"/g' /tmp/wms-scope-lookup.txt \
    | grep "\"name\":\"${SCOPE_NAME}\"" \
    | head -n 1 \
    | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'
}

client_has_default_scope() {
  CLIENT_NAME="$1"
  SCOPE_NAME="$2"
  CLIENT_UUID=$(lookup_client_uuid "${CLIENT_NAME}")
  if [ -z "$CLIENT_UUID" ]; then
    return 1
  fi

  HTTP_CODE=$(curl -s -o /tmp/wms-client-scopes.txt -w "%{http_code}" \
    "${KEYCLOAK_URL}/admin/realms/wms-realm/clients/${CLIENT_UUID}/default-client-scopes" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  if [ "$HTTP_CODE" != "200" ]; then
    return 1
  fi

  grep -q "\"name\"[[:space:]]*:[[:space:]]*\"${SCOPE_NAME}\"" /tmp/wms-client-scopes.txt
}

ensure_client_default_scopes() {
  CLIENT_NAME="$1"
  CLIENT_UUID=$(lookup_client_uuid "${CLIENT_NAME}")
  if [ -z "$CLIENT_UUID" ]; then
    echo "[wms-keycloak-init] ERROR: Client ${CLIENT_NAME} not found"
    return 1
  fi

  for SCOPE_NAME in $DEFAULT_SCOPES; do
    if client_has_default_scope "${CLIENT_NAME}" "${SCOPE_NAME}"; then
      continue
    fi

    SCOPE_UUID=$(lookup_scope_uuid "${SCOPE_NAME}")
    if [ -z "$SCOPE_UUID" ]; then
      echo "[wms-keycloak-init] ERROR: Client scope ${SCOPE_NAME} not found in realm"
      return 1
    fi

    HTTP_CODE=$(curl -s -o /tmp/wms-scope-assign.txt -w "%{http_code}" \
      -X PUT "${KEYCLOAK_URL}/admin/realms/wms-realm/clients/${CLIENT_UUID}/default-client-scopes/${SCOPE_UUID}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}")

    if [ "$HTTP_CODE" != "204" ] && [ "$HTTP_CODE" != "409" ]; then
      echo "[wms-keycloak-init] ERROR: Failed to assign ${SCOPE_NAME} to ${CLIENT_NAME} (HTTP ${HTTP_CODE})"
      cat /tmp/wms-scope-assign.txt
      return 1
    fi
    echo "[wms-keycloak-init] Assigned scope ${SCOPE_NAME} -> ${CLIENT_NAME}"
  done
}

import_realm() {
  HTTP_CODE=$(curl -s -o /tmp/wms-realm-import-response.txt -w "%{http_code}" \
    -X POST "${KEYCLOAK_URL}/admin/realms" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    --data-binary @"${REALM_FILE}")

  case "$HTTP_CODE" in
    201|204)
      echo "[wms-keycloak-init] Realm imported (HTTP ${HTTP_CODE})"
      sleep 2
      ;;
    409)
      echo "[wms-keycloak-init] Realm already exists (HTTP 409)"
      ;;
    *)
      echo "[wms-keycloak-init] ERROR: Import failed (HTTP ${HTTP_CODE})"
      cat /tmp/wms-realm-import-response.txt
      return 1
      ;;
  esac
}

delete_realm() {
  HTTP_CODE=$(curl -s -o /tmp/wms-realm-delete-response.txt -w "%{http_code}" \
    -X DELETE "${KEYCLOAK_URL}/admin/realms/wms-realm" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  if [ "$HTTP_CODE" != "204" ] && [ "$HTTP_CODE" != "404" ]; then
    echo "[wms-keycloak-init] ERROR: Failed to delete stale realm (HTTP ${HTTP_CODE})"
    cat /tmp/wms-realm-delete-response.txt
    return 1
  fi
  echo "[wms-keycloak-init] Removed stale wms-realm (HTTP ${HTTP_CODE})"
}

client_exists() {
  [ -n "$(lookup_client_uuid "${EXPECTED_CLIENT}")" ]
}

scope_exists() {
  [ -n "$(lookup_scope_uuid "$1")" ]
}

realm_is_ready() {
  client_exists \
    && client_has_default_scope "${API_CLIENT}" "${REQUIRED_SCOPE}" \
    && client_has_default_scope "${EXPECTED_CLIENT}" "${REQUIRED_SCOPE}"
}

ensure_user_profile() {
  PROFILE_FILE="${PROFILE_FILE:-/config/user-profile.json}"
  if [ ! -f "$PROFILE_FILE" ]; then
    echo "[wms-keycloak-init] WARN: user-profile.json not found — skip profile bootstrap"
    return 0
  fi

  HTTP_CODE=$(curl -s -o /tmp/wms-user-profile-response.txt -w "%{http_code}" \
    -X PUT "${KEYCLOAK_URL}/admin/realms/wms-realm/users/profile" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    --data-binary "@${PROFILE_FILE}")

  if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
    echo "[wms-keycloak-init] User profile configured (wms_user_id + unmanaged attributes ENABLED)"
    return 0
  fi

  echo "[wms-keycloak-init] WARN: User profile bootstrap failed (HTTP ${HTTP_CODE})"
  cat /tmp/wms-user-profile-response.txt
  return 0
}

sync_demo_user_wms_id() {
  DEMO_KC_ID="aaaaaaaa-0000-0000-0000-000000000001"
  EXPECTED_WMS_ID="1"

  HTTP_CODE=$(curl -s -o /tmp/wms-demo-user.txt -w "%{http_code}" \
    "${KEYCLOAK_URL}/admin/realms/wms-realm/users/${DEMO_KC_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  if [ "$HTTP_CODE" != "200" ]; then
    echo "[wms-keycloak-init] Demo user not found — skip wms_user_id sync (HTTP ${HTTP_CODE})"
    return 0
  fi

  CURRENT=$(sed -n 's/.*"wms_user_id"[[:space:]]*:[[:space:]]*\[[[:space:]]*"\([^"]*\)".*/\1/p' /tmp/wms-demo-user.txt | head -n 1)
  if [ "$CURRENT" = "$EXPECTED_WMS_ID" ]; then
    echo "[wms-keycloak-init] Demo user wms_user_id already ${EXPECTED_WMS_ID}"
    return 0
  fi

  USER_JSON=$(tr -d '\n' < /tmp/wms-demo-user.txt)
  PATCH_BODY=$(printf '%s' "$USER_JSON" | sed 's/"wms_user_id"[[:space:]]*:[[:space:]]*\[[^]]*\]/"wms_user_id":["'"${EXPECTED_WMS_ID}"'"]/')

  HTTP_CODE=$(curl -s -o /tmp/wms-demo-user-patch.txt -w "%{http_code}" \
    -X PUT "${KEYCLOAK_URL}/admin/realms/wms-realm/users/${DEMO_KC_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    --data-binary "$PATCH_BODY")

  if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
    echo "[wms-keycloak-init] Demo user wms_user_id synced -> ${EXPECTED_WMS_ID}"
    return 0
  fi

  echo "[wms-keycloak-init] WARN: Demo user wms_user_id sync failed (HTTP ${HTTP_CODE})"
  cat /tmp/wms-demo-user-patch.txt
  return 0
}

ADMIN_TOKEN=$(fetch_admin_token)
if [ -z "$ADMIN_TOKEN" ]; then
  echo "[wms-keycloak-init] ERROR: Failed to obtain admin token"
  exit 1
fi

if curl -sf "${KEYCLOAK_URL}/realms/wms-realm/.well-known/openid-configuration" >/dev/null 2>&1; then
  if realm_is_ready; then
    ensure_user_profile
    sync_demo_user_wms_id
    echo "[wms-keycloak-init] wms-realm ready (${EXPECTED_CLIENT} + ${API_CLIENT} scopes OK)"
    exit 0
  fi

  if client_exists; then
    if ! scope_exists "wms-basic"; then
      echo "[wms-keycloak-init] wms-realm missing custom scopes — re-importing..."
      delete_realm
      import_realm
    else
      echo "[wms-keycloak-init] wms-realm present but client scopes incomplete — repairing..."
    fi
    ensure_client_default_scopes "${EXPECTED_CLIENT}"
    ensure_client_default_scopes "${API_CLIENT}"
  else
    echo "[wms-keycloak-init] wms-realm outdated — re-importing..."
    delete_realm
    import_realm
    ensure_client_default_scopes "${EXPECTED_CLIENT}"
    ensure_client_default_scopes "${API_CLIENT}"
  fi
else
  echo "[wms-keycloak-init] wms-realm not found — importing..."
  import_realm
  ensure_client_default_scopes "${EXPECTED_CLIENT}"
  ensure_client_default_scopes "${API_CLIENT}"
fi

if realm_is_ready; then
  ensure_user_profile
  sync_demo_user_wms_id
  echo "[wms-keycloak-init] wms-realm bootstrap complete"
  exit 0
fi

echo "[wms-keycloak-init] ERROR: Realm bootstrap verification failed"
exit 1
