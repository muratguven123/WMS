import { FEATURES, isFeatureEnabled, isWmsAdmin } from "../auth/roles";
import { stompDestinations } from "./destinations";
import { useTopicSubscription } from "./StompProvider";

/**
 * Firma CRUD Kafka/STOMP olayında şirket listesini yeniler.
 * Yalnızca WMS_ADMIN `/topic/org.companies` abonesi olabilir.
 */
export function OrgCompaniesRealtimeSync({ onChanged }: { onChanged: () => void }) {
  const enabled =
    isWmsAdmin() && isFeatureEnabled(FEATURES.REALTIME) ? stompDestinations.orgCompanies() : null;

  useTopicSubscription(enabled, () => {
    onChanged();
  });

  return null;
}
