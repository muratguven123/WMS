import { useState, useEffect, useCallback } from "react";
import { outboundService } from "../api/services";
import { keycloak, tenantContext } from "../api/wms-api-client";
import { stompDestinations } from "../realtime/destinations";
import { useStomp, useTopicSubscription } from "../realtime/StompProvider";
import { useI18n } from "../i18n/I18nContext";
import {
  ListTodo,
  CheckCircle,
  MapPin,
  Compass,
  Package
} from "lucide-react";

export interface PickingItem {
  id: number;
  sku: string;
  name: string;
  binId: string;
  qtyToPick: number;
  qtyPicked: number;
  status: "PENDING" | "PICKED";
  routeOrder: number;
}

export interface PickingList {
  id: number;
  createdAt: string;
  status: "PENDING" | "PICKING" | "PICKED";
  items: PickingItem[];
}

const INITIAL_PICKING_LISTS: PickingList[] = [
  {
    id: 901,
    createdAt: "06.07.2026 08:15",
    status: "PICKING",
    items: [
      {
        id: 1,
        sku: "SKU-MON-100",
        name: "Premium LED Monitor 27\"",
        binId: "A-01-01-01",
        qtyToPick: 5,
        qtyPicked: 0,
        status: "PENDING",
        routeOrder: 1,
      },
      {
        id: 2,
        sku: "SKU-KEY-500",
        name: "Mechanical Keyboard RGB",
        binId: "A-02-03-02",
        qtyToPick: 12,
        qtyPicked: 0,
        status: "PENDING",
        routeOrder: 2,
      },
      {
        id: 3,
        sku: "SKU-CAB-090",
        name: "USB-C Braided Cable 2m",
        binId: "B-01-02-01",
        qtyToPick: 40,
        qtyPicked: 0,
        status: "PENDING",
        routeOrder: 3,
      },
      {
        id: 4,
        sku: "SKU-MOU-400",
        name: "Wireless Ergonomic Mouse",
        binId: "B-04-03-02",
        qtyToPick: 10,
        qtyPicked: 0,
        status: "PENDING",
        routeOrder: 4,
      },
    ],
  },
  {
    id: 902,
    createdAt: "06.07.2026 08:30",
    status: "PENDING",
    items: [
      {
        id: 5,
        sku: "SKU-KEY-500",
        name: "Mechanical Keyboard RGB",
        binId: "A-04-02-01",
        qtyToPick: 8,
        qtyPicked: 0,
        status: "PENDING",
        routeOrder: 1,
      },
      {
        id: 6,
        sku: "SKU-MOU-400",
        name: "Wireless Ergonomic Mouse",
        binId: "C-01-01-02",
        qtyToPick: 15,
        qtyPicked: 0,
        status: "PENDING",
        routeOrder: 2,
      },
    ],
  },
];

interface PickingRouteProps {
  onNavigateToShipping: (pickingListId: number) => void;
  activeLocationId?: number | "";
}

export const PickingRoute: React.FC<PickingRouteProps> = ({ onNavigateToShipping, activeLocationId }) => {
  const { t } = useI18n();
  const [pickingLists, setPickingLists] = useState<PickingList[]>(INITIAL_PICKING_LISTS);
  const [unassignedLists, setUnassignedLists] = useState<PickingList[]>([]);
  const [selectedListId, setSelectedListId] = useState<number>(901);
  const { userId } = useStomp();

  const mapApiList = useCallback((pl: import("../api/services").PickingListResponse): PickingList => ({
    id: pl.id,
    createdAt: new Date().toLocaleString(),
    status: pl.status === "COMPLETED" ? "PICKED" : pl.status === "PENDING" || pl.status === "ASSIGNED" ? "PENDING" : "PICKING",
    items: pl.items.map((it, i) => ({
      id: it.id,
      sku: it.productCode,
      name: it.productCode,
      binId: it.addressCode ?? (it.sourceLocationId != null ? String(it.sourceLocationId) : "—"),
      qtyToPick: Number(it.quantityToPick ?? it.quantity ?? 0),
      qtyPicked: Number(it.pickedQuantity ?? 0),
      status: it.status === "PICKED" || Number(it.pickedQuantity) >= Number(it.quantityToPick) ? "PICKED" : "PENDING",
      routeOrder: it.sequence ?? i + 1,
    })),
  }), []);

  const loadTasks = useCallback(() => {
    if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) return;
    Promise.allSettled([
      outboundService.listMyTasks(),
      outboundService.listUnassignedTasks(),
    ]).then(([my, unassigned]) => {
      if (my.status === "fulfilled" && my.value.length > 0) {
        const mapped = my.value.map((pl) => mapApiList(pl));
        setPickingLists(mapped);
        if (mapped[0]?.id != null) setSelectedListId(mapped[0].id);
      } else {
        outboundService.listPickingLists().then((page) => {
          if (page.content.length === 0) return;
          const mapped = page.content.map((pl) => mapApiList(pl));
          setPickingLists(mapped);
          if (mapped[0]?.id != null) setSelectedListId(mapped[0].id);
        }).catch(() => { /* mock */ });
      }
      if (unassigned.status === "fulfilled") {
        setUnassignedLists(unassigned.value.map((pl) => mapApiList(pl)));
      }
    });
  }, [mapApiList]);

  useEffect(() => {
    loadTasks();
  }, [loadTasks, activeLocationId]);

  const companyId = tenantContext.getCompanyId();
  const warehouseId = activeLocationId || tenantContext.getLocationId();

  useTopicSubscription(
    userId ? stompDestinations.userTasks(userId) : null,
    () => loadTasks(),
  );

  useTopicSubscription(
    companyId && warehouseId ? stompDestinations.tasks(companyId, warehouseId) : null,
    () => loadTasks(),
  );

  const selectedList = pickingLists.find((list) => list.id === selectedListId);

  const activeStep = selectedList?.items
    .filter((item) => item.status === "PENDING")
    .sort((a, b) => a.routeOrder - b.routeOrder)[0];

  const handleConfirmPick = async (itemId: number) => {
    if (!selectedList) return;
    const item = selectedList.items.find((i) => i.id === itemId);
    if (!item) return;

    if (!(window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) {
      try {
        await outboundService.confirmPick(itemId, item.qtyToPick - item.qtyPicked);
        loadTasks();
        return;
      } catch {
        /* fall through to local update */
      }
    }

    const updatedLists = pickingLists.map((list) => {
      if (list.id !== selectedList.id) return list;

      const updatedItems = list.items.map((item) => {
        if (item.id !== itemId) return item;
        return {
          ...item,
          status: "PICKED" as const,
          qtyPicked: item.qtyToPick,
        };
      });

      const allPicked = updatedItems.every((item) => item.status === "PICKED");
      const listStatus = allPicked ? ("PICKED" as const) : ("PICKING" as const);

      return {
        ...list,
        status: listStatus,
        items: updatedItems,
      };
    });

    setPickingLists(updatedLists);
  };

  const handleAssignToMe = async (listId: number) => {
    const sub = keycloak.subject;
    if (!sub || (window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) return;
    try {
      await outboundService.assignPickingList(listId, sub);
      loadTasks();
    } catch (e) {
      console.warn("Assign failed", e);
    }
  };

  return (
    <div style={styles.container}>
      <div className="layout-split">
        <div className="glass-card" style={styles.listCard}>
          <div style={styles.cardHeader}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <ListTodo size={20} color="var(--neon-blue)" />
              <h3 style={{ fontSize: "1.1rem" }}>{t("ops.picking.activeLists")}</h3>
            </div>
          </div>

          <div style={styles.listsContainer}>
            {unassignedLists.length > 0 && (
              <div style={{ marginBottom: 12, padding: 10, border: "1px dashed var(--neon-orange)", borderRadius: 8 }}>
                <div style={{ fontSize: "0.75rem", color: "var(--neon-orange)", marginBottom: 8 }}>{t("ops.picking.unassigned")}</div>
                {unassignedLists.map((list) => (
                  <button
                    key={`unassigned-${list.id}`}
                    type="button"
                    className="btn btn-secondary"
                    style={{ width: "100%", marginBottom: 6, fontSize: "0.8rem" }}
                    onClick={() => void handleAssignToMe(list.id)}
                  >
                    {t("ops.picking.assignToMe").replace("{id}", String(list.id))}
                  </button>
                ))}
              </div>
            )}
            {pickingLists.map((list) => {
              const totalItems = list.items.length;
              const pickedItems = list.items.filter((i) => i.status === "PICKED").length;
              const isActive = list.id === selectedListId;
              
              return (
                <div
                  key={list.id}
                  onClick={() => setSelectedListId(list.id)}
                  style={{
                    ...styles.listSelectorCard,
                    borderColor: isActive ? "var(--neon-blue)" : "rgba(255, 255, 255, 0.08)",
                    background: isActive ? "rgba(0, 210, 255, 0.05)" : "rgba(14, 19, 34, 0.4)",
                    boxShadow: isActive ? "var(--shadow-neon-blue)" : "none",
                  }}
                >
                  <div style={styles.listCardTitleRow}>
                    <span style={styles.listId}>#{list.id}</span>
                    <span className={`badge ${
                      list.status === "PENDING" 
                        ? "badge-orange" 
                        : list.status === "PICKING" 
                          ? "badge-blue" 
                          : "badge-green"
                    }`}>
                      {list.status === "PENDING" ? t("ops.picking.statusPending") : list.status === "PICKING" ? t("ops.picking.statusPicking") : t("ops.picking.statusPicked")}
                    </span>
                  </div>
                  
                  <div style={styles.listCardDetails}>
                    <span>{t("ops.picking.date").replace("{date}", list.createdAt)}</span>
                    <span>{t("ops.picking.progress").replace("{picked}", String(pickedItems)).replace("{total}", String(totalItems))}</span>
                  </div>

                  <div className="capacity-bar-container" style={{ height: "4px", marginTop: "8px" }}>
                    <div
                      className="capacity-bar green"
                      style={{ width: `${(pickedItems / totalItems) * 100}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="glass-card" style={styles.routeCard}>
          {selectedList ? (
            <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
              <div style={styles.cardHeader}>
                <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                  <Compass size={22} color="var(--neon-blue)" />
                  <h3 style={{ fontSize: "1.1rem" }}>{t("ops.picking.routeGuide")}</h3>
                </div>
              </div>

              {selectedList.status === "PICKED" ? (
                <div style={styles.completedRouteBox}>
                  <CheckCircle size={48} color="var(--neon-green)" style={{ marginBottom: "15px" }} />
                  <h4>{t("ops.picking.allPicked")}</h4>
                  <p style={{ color: "var(--text-secondary)", fontSize: "0.9rem", margin: "10px 0 20px" }}>
                    {t("ops.picking.readyForPacking").replace("{id}", String(selectedList.id))}
                  </p>
                  <button
                    className="btn btn-success"
                    onClick={() => onNavigateToShipping(selectedList.id)}
                  >
                    <Package size={18} /> {t("ops.picking.goToShipping")}
                  </button>
                </div>
              ) : (
                <div style={styles.routeContainer}>
                  {activeStep && (
                    <div className="glass-card" style={styles.activeStepPanel}>
                      <div style={styles.activeHeading}>
                        <span style={styles.pulseDot} />
                        <span>{t("ops.picking.nextAddress")}</span>
                      </div>
                      <div style={styles.activeLocationRow}>
                        <MapPin size={28} color="var(--neon-blue)" />
                        <h2 style={styles.activeLocationBin}>{activeStep.binId}</h2>
                      </div>
                      
                      <div style={styles.activeProductBox}>
                        <span style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>{t("ops.picking.skuToPick")}</span>
                        <h4 style={{ color: "var(--text-primary)", fontSize: "1rem", margin: "4px 0" }}>{activeStep.sku}</h4>
                        <p style={{ color: "var(--text-secondary)", fontSize: "0.8rem" }}>{activeStep.name}</p>
                        
                        <div style={styles.activeQtyRow}>
                          <span>{t("ops.picking.requiredQty")}</span>
                          <strong style={styles.activeQtyValue}>{t("ops.picking.qtyUnit").replace("{qty}", String(activeStep.qtyToPick))}</strong>
                        </div>
                      </div>

                      <button
                        className="btn btn-primary"
                        style={{ width: "100%", marginTop: "15px" }}
                        onClick={() => handleConfirmPick(activeStep.id)}
                      >
                        <CheckCircle size={18} /> {t("ops.picking.confirmPick")}
                      </button>
                    </div>
                  )}

                  <div style={styles.timelineList}>
                    <h4 style={styles.routeOrderTitle}>{t("ops.picking.routeOrder")}</h4>
                    
                    <div style={styles.timelineContainer}>
                      {selectedList.items
                        .sort((a, b) => a.routeOrder - b.routeOrder)
                        .map((item, idx) => {
                          const isPicked = item.status === "PICKED";
                          const isActive = activeStep?.id === item.id;

                          return (
                            <div key={item.id} style={styles.timelineItem}>
                              <div style={styles.timelineIndicatorColumn}>
                                <div
                                  style={{
                                    ...styles.timelinePoint,
                                    borderColor: isPicked 
                                      ? "var(--neon-green)" 
                                      : isActive 
                                        ? "var(--neon-blue)" 
                                        : "var(--glass-border)",
                                    background: isPicked 
                                      ? "var(--neon-green)" 
                                      : isActive 
                                        ? "rgba(0, 210, 255, 0.2)" 
                                        : "var(--bg-primary)",
                                    boxShadow: isPicked 
                                      ? "var(--shadow-neon-green)" 
                                      : isActive 
                                        ? "var(--shadow-neon-blue)" 
                                        : "none"
                                  }}
                                  className={isActive ? "pulse-route" : ""}
                                >
                                  {isPicked ? (
                                    <CheckCircle size={12} color="var(--bg-primary)" />
                                  ) : (
                                    <span style={{ fontSize: "0.65rem", fontWeight: 700 }}>{item.routeOrder}</span>
                                  )}
                                </div>
                                {idx < selectedList.items.length - 1 && (
                                  <div
                                    style={{
                                      ...styles.timelineLine,
                                      background: isPicked ? "var(--neon-green)" : "var(--glass-border)",
                                    }}
                                  />
                                )}
                              </div>

                              <div
                                style={{
                                  ...styles.timelineDetails,
                                  opacity: isPicked ? 0.5 : 1,
                                  borderColor: isActive ? "var(--neon-blue)" : "var(--glass-border)"
                                }}
                              >
                                <div style={styles.timelineHeaderRow}>
                                  <span style={{
                                    ...styles.timelineBin,
                                    color: isPicked ? "var(--neon-green)" : "var(--text-primary)",
                                    textDecoration: isPicked ? "line-through" : "none"
                                  }}>
                                    {item.binId}
                                  </span>
                                  <span className="badge badge-purple" style={{ fontSize: "0.6rem" }}>
                                    {t("ops.picking.qtyUnit").replace("{qty}", String(item.qtyToPick))}
                                  </span>
                                </div>
                                <div style={styles.timelineSkuName}>{item.sku} - {item.name}</div>
                              </div>
                            </div>
                          );
                        })}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div style={styles.emptyContainer}>
              <ListTodo size={48} color="var(--text-muted)" style={{ marginBottom: "15px" }} />
              <p>{t("ops.picking.selectList")}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
  },
  listCard: {
    padding: "20px",
    display: "flex",
    flexDirection: "column",
    maxHeight: "75vh",
    overflowY: "auto",
  },
  cardHeader: {
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "15px",
    marginBottom: "15px",
  },
  listsContainer: {
    display: "flex",
    flexDirection: "column",
    gap: "12px",
  },
  listSelectorCard: {
    border: "1px solid var(--glass-border)",
    borderRadius: "10px",
    padding: "16px",
    cursor: "pointer",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
    textAlign: "left",
    transition: "var(--transition-smooth)",
  },
  listCardTitleRow: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
  },
  listId: {
    fontWeight: 700,
    fontSize: "0.95rem",
    color: "var(--text-primary)",
  },
  listCardDetails: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.8rem",
    color: "var(--text-muted)",
  },
  routeCard: {
    padding: "25px",
    minHeight: "50vh",
    display: "flex",
    flexDirection: "column",
  },
  completedRouteBox: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    flexGrow: 1,
    padding: "40px 10px",
    textAlign: "center",
  },
  routeContainer: {
    display: "grid",
    gridTemplateColumns: "1.2fr 1fr",
    gap: "20px",
  },
  activeStepPanel: {
    padding: "20px",
    textAlign: "left",
    background: "rgba(0, 210, 255, 0.03)",
    borderColor: "rgba(0, 210, 255, 0.2)",
    height: "fit-content",
  },
  activeHeading: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    fontSize: "0.75rem",
    textTransform: "uppercase",
    color: "var(--neon-blue)",
    fontWeight: 600,
    marginBottom: "12px",
  },
  pulseDot: {
    width: "8px",
    height: "8px",
    borderRadius: "50%",
    backgroundColor: "var(--neon-blue)",
    boxShadow: "var(--shadow-neon-blue)",
  },
  activeLocationRow: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    marginBottom: "15px",
  },
  activeLocationBin: {
    fontSize: "1.8rem",
    fontWeight: 700,
    color: "var(--neon-blue)",
    textShadow: "0 0 10px rgba(0, 210, 255, 0.2)",
  },
  activeProductBox: {
    background: "rgba(0, 0, 0, 0.2)",
    borderRadius: "8px",
    padding: "15px",
    border: "1px solid var(--glass-border)",
  },
  activeQtyRow: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderTop: "1px solid var(--glass-border)",
    paddingTop: "10px",
    marginTop: "12px",
    fontSize: "0.9rem",
  },
  activeQtyValue: {
    fontSize: "1.1rem",
    color: "var(--neon-green)",
  },
  timelineList: {
    display: "flex",
    flexDirection: "column",
    textAlign: "left",
  },
  routeOrderTitle: {
    fontSize: "0.85rem",
    color: "var(--text-secondary)",
    marginBottom: "15px",
    fontWeight: 600,
  },
  timelineContainer: {
    display: "flex",
    flexDirection: "column",
    paddingLeft: "10px",
  },
  timelineItem: {
    display: "grid",
    gridTemplateColumns: "30px 1fr",
    gap: "12px",
  },
  timelineIndicatorColumn: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
  },
  timelinePoint: {
    width: "22px",
    height: "22px",
    borderRadius: "50%",
    border: "1px solid var(--glass-border)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 2,
    transition: "var(--transition-smooth)",
  },
  timelineLine: {
    width: "2px",
    flexGrow: 1,
    minHeight: "40px",
    zIndex: 1,
  },
  timelineDetails: {
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    background: "rgba(14, 19, 34, 0.3)",
    padding: "10px 14px",
    marginBottom: "12px",
    transition: "var(--transition-smooth)",
  },
  timelineHeaderRow: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: "4px",
  },
  timelineBin: {
    fontSize: "0.9rem",
    fontWeight: 700,
  },
  timelineSkuName: {
    fontSize: "0.75rem",
    color: "var(--text-secondary)",
  },
  emptyContainer: {
    flexGrow: 1,
    display: "flex",
    flexDirection: "column",
    justifyContent: "center",
    alignItems: "center",
    color: "var(--text-muted)",
  },
};

if (typeof document !== 'undefined') {
  const styleEl = document.createElement("style");
  styleEl.innerHTML += `
    .timeline-details:hover {
      background: rgba(255, 255, 255, 0.02) !important;
      border-color: rgba(255, 255, 255, 0.15) !important;
    }
  `;
  document.head.appendChild(styleEl);
}
