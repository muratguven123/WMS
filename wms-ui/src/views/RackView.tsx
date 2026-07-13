import { useState, useEffect, useCallback } from "react";
import {
  Layers,
  Unlock,
  Lock,
  Boxes,
  X,
  Info,
  Scale
} from "lucide-react";
import { storageLocationService, type StorageLocationResponse } from "../api/services";
import { tenantContext } from "../api/wms-api-client";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";
import { useI18n } from "../i18n/I18nContext";

export interface SKUItem {
  sku: string;
  name: string;
  qty: number;
  lot: string;
}

export interface StorageLocation {
  id: number;
  code: string;
  aisle: string;
  bay: string;
  level: string;
  bin: string;
  status: "ACTIVE" | "BLOCKED" | "FULL";
  volumeOccupied: number; // m³
  maxVolume: number;      // m³
  weightOccupied: number; // kg
  maxWeight: number;      // kg
  skus: SKUItem[];
}

// Global shared mock data to make mutations feel alive in UI
export let MOCK_LOCATIONS_DATA: StorageLocation[] = [];

// Initialize mock locations
const initMockLocations = () => {
  if (MOCK_LOCATIONS_DATA.length > 0) return;
  
  const aisles = ["A", "B", "C"];
  const bays = ["01", "02", "03", "04"];
  const levels = ["03", "02", "01"]; // top to bottom
  const bins = ["01", "02"];

  const sampleSKUs = [
    { sku: "SKU-MON-100", name: "Premium LED Monitor 27\"", lot: "L-2026-A" },
    { sku: "SKU-KEY-500", name: "Mechanical Keyboard RGB", lot: "L-2026-B" },
    { sku: "SKU-CAB-090", name: "USB-C Braided Cable 2m", lot: "L-2026-C" },
    { sku: "SKU-MOU-400", name: "Wireless Ergonomic Mouse", lot: "L-2026-A" },
  ];

  let mockId = 1;
  aisles.forEach((aisle) => {
    bays.forEach((bay) => {
      levels.forEach((level) => {
        bins.forEach((bin) => {
          const code = `${aisle}-${bay}-${level}-${bin}`;
          
          // Randomize status and contents
          const rand = Math.random();
          let status: "ACTIVE" | "BLOCKED" | "FULL" = "ACTIVE";
          let skus: SKUItem[] = [];
          let volumeOccupied = 0;
          let weightOccupied = 0;

          if (rand > 0.85) {
            status = "BLOCKED";
            // Karantina/QC blocked item
            skus = [{ ...sampleSKUs[2], qty: 150, lot: "L-BLOCKED" }];
            volumeOccupied = 4.2;
            weightOccupied = 85;
          } else if (rand > 0.65) {
            status = "FULL";
            skus = [
              { ...sampleSKUs[0], qty: 40, lot: "L-2026-A" },
              { ...sampleSKUs[1], qty: 30, lot: "L-2026-B" },
            ];
            volumeOccupied = 9.8;
            weightOccupied = 280;
          } else if (rand > 0.2) {
            status = "ACTIVE";
            const numItems = Math.floor(Math.random() * 2) + 1;
            for (let i = 0; i < numItems; i++) {
              const item = sampleSKUs[Math.floor(Math.random() * sampleSKUs.length)];
              skus.push({ ...item, qty: Math.floor(Math.random() * 50) + 10 });
            }
            volumeOccupied = Number((Math.random() * 6 + 1).toFixed(1));
            weightOccupied = Math.floor(Math.random() * 150) + 20;
          }

          MOCK_LOCATIONS_DATA.push({
            id: mockId++,
            code,
            aisle,
            bay,
            level,
            bin,
            status,
            volumeOccupied,
            maxVolume: 10, // Max 10 m3
            weightOccupied,
            maxWeight: 300, // Max 300 kg
            skus
          });
        });
      });
    });
  });
};

interface RackViewProps {
  highlightedBinId?: string;
  clearHighlight?: () => void;
  activeLocationId?: number | "";
}

const isOffline = () => Boolean((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode);

function mapApiLocation(r: StorageLocationResponse): StorageLocation {
  const volPct = r.volumeUtilizationPercent ?? 0;
  const code = r.addressCode ?? ([r.aisle, r.bay, r.shelf, r.bin].filter(Boolean).join("-") || String(r.id));
  return {
    id: r.id,
    code,
    aisle: r.aisle ?? "A",
    bay: r.bay ?? "01",
    level: r.shelf ?? "01",
    bin: r.bin ?? "01",
    status: !r.active ? "BLOCKED" : volPct >= 95 ? "FULL" : "ACTIVE",
    volumeOccupied: r.currentVolume ?? 0,
    maxVolume: r.maxVolume ?? 10,
    weightOccupied: r.currentWeight ?? 0,
    maxWeight: r.maxWeight ?? 300,
    skus: [],
  };
}

export const RackView: React.FC<RackViewProps> = ({
  highlightedBinId,
  clearHighlight,
  activeLocationId,
}) => {
  const { t } = useI18n();
  useEffect(() => {
    if (isOffline()) {
      initMockLocations();
      setLocations([...MOCK_LOCATIONS_DATA]);
      return;
    }
    storageLocationService
      .search({ isActive: true, size: 200 })
      .then((page) => {
        const mapped = page.content.map(mapApiLocation);
        if (mapped.length > 0) {
          MOCK_LOCATIONS_DATA = mapped;
          setLocations(mapped);
        } else {
          initMockLocations();
          setLocations([...MOCK_LOCATIONS_DATA]);
        }
      })
      .catch(() => {
        initMockLocations();
        setLocations([...MOCK_LOCATIONS_DATA]);
      });
  }, [activeLocationId]);

  const [selectedAisle, setSelectedAisle] = useState("A");
  const [selectedBin, setSelectedBin] = useState<StorageLocation | null>(null);
  const [locations, setLocations] = useState<StorageLocation[]>(MOCK_LOCATIONS_DATA);

  const companyId = tenantContext.getCompanyId();
  const warehouseLocationId = activeLocationId || tenantContext.getLocationId();

  const patchLocation = useCallback((locId: number, patch: Partial<StorageLocation>) => {
    setLocations((prev) => {
      const next = prev.map((l) => (l.id === locId ? { ...l, ...patch } : l));
      MOCK_LOCATIONS_DATA = next;
      return next;
    });
  }, []);

  useTopicSubscription(
    companyId && warehouseLocationId
      ? stompDestinations.locations(companyId, warehouseLocationId)
      : null,
    (event) => {
      const p = event.payload;
      const locId = Number(p.storageLocationId ?? p.locationId ?? 0);
      if (!locId) return;
      const blocked = Boolean(p.blocked);
      const status = blocked ? "BLOCKED" : p.status === "FULL" ? "FULL" : "ACTIVE";
      patchLocation(locId, { status: status as StorageLocation["status"] });
    },
  );

  useTopicSubscription(
    companyId && warehouseLocationId
      ? stompDestinations.stock(companyId, warehouseLocationId)
      : null,
    (event) => {
      const p = event.payload;
      const binId = Number(p.binId ?? p.storageLocationId ?? 0);
      const sku = String(p.productCode ?? p.skuId ?? "");
      const delta = Number(p.delta ?? 0);
      if (!binId || !sku || !delta) return;
      setLocations((prev) => {
        const next = prev.map((loc) => {
          if (loc.id !== binId) return loc;
          const existing = loc.skus.find((s) => s.sku === sku);
          let skus = [...loc.skus];
          if (existing) {
            const newQty = Math.max(0, existing.qty + delta);
            skus = newQty === 0
              ? skus.filter((s) => s.sku !== sku)
              : skus.map((s) => (s.sku === sku ? { ...s, qty: newQty } : s));
          } else if (delta > 0) {
            skus.push({ sku, name: sku, qty: delta, lot: "LIVE" });
          }
          return { ...loc, skus };
        });
        MOCK_LOCATIONS_DATA = next;
        return next;
      });
    },
  );

  // Sync state if global data changes
  const refreshLocations = () => {
    setLocations([...MOCK_LOCATIONS_DATA]);
    if (selectedBin) {
      const updated = MOCK_LOCATIONS_DATA.find((l) => l.id === selectedBin.id);
      if (updated) setSelectedBin(updated);
    }
  };

  useEffect(() => {
    refreshLocations();
  }, [highlightedBinId]);

  // Handle toggling BLOCKED status
  const handleToggleBlock = (binId: number) => {
    const loc = MOCK_LOCATIONS_DATA.find((l) => l.id === binId);
    if (loc) {
      loc.status = loc.status === "BLOCKED" ? "ACTIVE" : "BLOCKED";
      refreshLocations();
    }
  };

  // Get distinct levels and bays for rendering grid
  const distinctBays = ["01", "02", "03", "04"];
  const distinctLevels = ["03", "02", "01"]; // Top shelf to bottom

  // Filter locations in the current Aisle
  const aisleLocations = locations.filter((loc) => loc.aisle === selectedAisle);

  return (
    <div style={styles.container}>
      {/* AISLE SELECTOR TABS */}
      <div style={styles.tabContainer}>
        {["A", "B", "C"].map((aisle) => (
          <button
            key={aisle}
            onClick={() => setSelectedAisle(aisle)}
            style={{
              ...styles.tabBtn,
              borderColor: selectedAisle === aisle ? "var(--neon-blue)" : "transparent",
              color: selectedAisle === aisle ? "var(--neon-blue)" : "var(--text-secondary)",
              boxShadow: selectedAisle === aisle ? "var(--shadow-neon-blue)" : "none",
            }}
          >
            <Layers size={16} />
            Koridor {aisle}
          </button>
        ))}
        {highlightedBinId && (
          <div className="badge badge-purple pulse-target" style={{ marginLeft: "auto", animationDuration: "1s" }}>
            Önerilen Hedef: {highlightedBinId}
            <button
              onClick={clearHighlight}
              style={{ background: "transparent", border: "none", marginLeft: "8px", color: "var(--neon-purple)", cursor: "pointer" }}
            >
              <X size={12} />
            </button>
          </div>
        )}
      </div>

      {/* 2D PHYSICAL GRID MAP */}
      <div className="glass-card" style={styles.gridCard}>
        <div style={styles.legend}>
          <div style={styles.legendItem}>
            <div style={{ ...styles.legendDot, background: "var(--neon-green)" }} />
            <span>{t("ops.rack.legendActive")}</span>
          </div>
          <div style={styles.legendItem}>
            <div style={{ ...styles.legendDot, background: "var(--neon-orange)" }} />
            <span>{t("ops.rack.legendBlocked")}</span>
          </div>
          <div style={styles.legendItem}>
            <div style={{ ...styles.legendDot, background: "var(--neon-red)" }} />
            <span>{t("ops.rack.legendFull")}</span>
          </div>
        </div>

        <div style={styles.rackMatrixContainer}>
          {distinctLevels.map((level) => (
            <div key={level} style={styles.matrixRow}>
              {/* Row Label (Level) */}
              <div style={styles.rowLabel}>Kat {level}</div>
              
              {/* Bays */}
              <div style={styles.rowBays}>
                {distinctBays.map((bay) => {
                  // Find the bins inside Aisle X, Bay Y, Level Z
                  const binsInCell = aisleLocations.filter(
                    (loc) => loc.bay === bay && loc.level === level
                  );

                  return (
                    <div key={bay} style={styles.bayCell}>
                      <span style={styles.bayLabel}>Bölüm {bay}</span>
                      
                      {/* Bins container */}
                      <div style={styles.binsFlex}>
                        {binsInCell.map((binLoc) => {
                          const isHighlighted = highlightedBinId === binLoc.code;
                          const volPercent = Math.min(100, Math.round((binLoc.volumeOccupied / binLoc.maxVolume) * 100));
                          const wtPercent = Math.min(100, Math.round((binLoc.weightOccupied / binLoc.maxWeight) * 100));
                          const maxPercent = Math.max(volPercent, wtPercent);

                          // State color mapping
                          let stateColor = "var(--neon-green)";
                          if (binLoc.status === "BLOCKED") {
                            stateColor = "var(--neon-orange)";
                          } else if (binLoc.status === "FULL" || maxPercent >= 95) {
                            stateColor = "var(--neon-red)";
                          }

                          return (
                            <div
                              key={binLoc.id}
                              onClick={() => setSelectedBin(binLoc)}
                              className={`bin-grid-element ${isHighlighted ? "pulse-target" : ""}`}
                              style={{
                                ...styles.binBox,
                                borderColor: isHighlighted ? "var(--neon-purple)" : "rgba(255, 255, 255, 0.08)",
                                borderTop: `3px solid ${stateColor}`,
                                background: isHighlighted 
                                  ? "rgba(217, 70, 239, 0.12)" 
                                  : selectedBin?.id === binLoc.id
                                    ? "rgba(0, 210, 255, 0.08)"
                                    : "rgba(14, 19, 34, 0.5)"
                              }}
                            >
                              <div style={styles.binBoxId}>{binLoc.code}</div>
                              
                              <div style={styles.barMiniContainer}>
                                <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.6rem", color: "var(--text-muted)", marginBottom: "2px" }}>
                                  <span>Doluluk</span>
                                  <span>{maxPercent}%</span>
                                </div>
                                <div className="capacity-bar-container" style={{ height: "4px" }}>
                                  <div
                                    className={`capacity-bar ${maxPercent >= 90 ? "red" : maxPercent >= 75 ? "orange" : "green"}`}
                                    style={{ width: `${maxPercent}%` }}
                                  />
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* SLIDING RIGHT DETAIL DRAWER */}
      <div className={`drawer-backdrop ${selectedBin ? "open" : ""}`} onClick={() => setSelectedBin(null)}>
        <div className="drawer-content" onClick={(e) => e.stopPropagation()}>
          <div className="drawer-header">
            <div>
              <span style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--text-muted)" }}>{t("ops.rack.storageAddress")}</span>
              <h3>Göz Detayı: {selectedBin?.id}</h3>
            </div>
            <button className="close-btn" onClick={() => setSelectedBin(null)}>
              <X size={20} />
            </button>
          </div>

          {selectedBin && (
            <div style={styles.drawerBody}>
              {/* Status Indicator */}
              <div style={styles.drawerStatRow}>
                <span>Durum</span>
                <span className={`badge ${
                  selectedBin.status === "ACTIVE" 
                    ? "badge-green" 
                    : selectedBin.status === "BLOCKED" 
                      ? "badge-orange" 
                      : "badge-red"
                }`}>
                  {selectedBin.status}
                </span>
              </div>

              {/* Volume Meter */}
              <div style={styles.drawerCapacitySection}>
                <div style={styles.capacityLabelRow}>
                  <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    <Boxes size={16} color="var(--neon-blue)" />
                    <span>Hacim Kapasitesi</span>
                  </div>
                  <span>{selectedBin.volumeOccupied} / {selectedBin.maxVolume} m³</span>
                </div>
                <div className="capacity-bar-container">
                  <div
                    className={`capacity-bar ${
                      (selectedBin.volumeOccupied / selectedBin.maxVolume) >= 0.9 
                        ? "red" 
                        : (selectedBin.volumeOccupied / selectedBin.maxVolume) >= 0.75 
                          ? "orange" 
                          : "green"
                    }`}
                    style={{ width: `${(selectedBin.volumeOccupied / selectedBin.maxVolume) * 100}%` }}
                  />
                </div>
              </div>

              {/* Weight Meter */}
              <div style={styles.drawerCapacitySection}>
                <div style={styles.capacityLabelRow}>
                  <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    <Scale size={16} color="var(--neon-purple)" />
                    <span>Ağırlık Kapasitesi</span>
                  </div>
                  <span>{selectedBin.weightOccupied} / {selectedBin.maxWeight} kg</span>
                </div>
                <div className="capacity-bar-container">
                  <div
                    className={`capacity-bar ${
                      (selectedBin.weightOccupied / selectedBin.maxWeight) >= 0.9 
                        ? "red" 
                        : (selectedBin.weightOccupied / selectedBin.maxWeight) >= 0.75 
                          ? "orange" 
                          : "green"
                    }`}
                    style={{ width: `${(selectedBin.weightOccupied / selectedBin.maxWeight) * 100}%` }}
                  />
                </div>
              </div>

              {/* SKU List */}
              <div style={{ marginTop: "25px" }}>
                <h4 style={styles.sectionHeader}>Depolanan Ürünler (SKU Listesi)</h4>
                {selectedBin.skus.length === 0 ? (
                  <div style={styles.emptySkuBox}>
                    <Info size={16} />
                    <span>Bu göz şu anda tamamen boştur.</span>
                  </div>
                ) : (
                  <div style={styles.skuList}>
                    {selectedBin.skus.map((skuItem, i) => (
                      <div key={i} style={styles.skuItemCard}>
                        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px" }}>
                          <span style={styles.skuCode}>{skuItem.sku}</span>
                          <span style={styles.skuQty}>{skuItem.qty} Adet</span>
                        </div>
                        <div style={styles.skuName}>{skuItem.name}</div>
                        <div style={styles.skuLot}>Lot: {skuItem.lot}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Actions Section */}
              <div style={styles.drawerActions}>
                <button
                  onClick={() => handleToggleBlock(selectedBin.id)}
                  className={`btn ${selectedBin.status === "BLOCKED" ? "btn-success" : "btn-danger"}`}
                  style={{ width: "100%" }}
                >
                  {selectedBin.status === "BLOCKED" ? (
                    <>
                      <Unlock size={16} /> Bloke Kaldır (ACTIVE Yap)
                    </>
                  ) : (
                    <>
                      <Lock size={16} /> Depo Gözünü Bloke Et
                    </>
                  )}
                </button>
              </div>
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
  tabContainer: {
    display: "flex",
    gap: "10px",
    alignItems: "center",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "12px",
  },
  tabBtn: {
    display: "inline-flex",
    alignItems: "center",
    gap: "8px",
    background: "var(--bg-secondary)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "8px 16px",
    fontSize: "0.85rem",
    fontWeight: 600,
    cursor: "pointer",
    transition: "var(--transition-smooth)",
  },
  gridCard: {
    padding: "25px",
  },
  legend: {
    display: "flex",
    gap: "20px",
    justifyContent: "flex-end",
    marginBottom: "20px",
    fontSize: "0.8rem",
    color: "var(--text-secondary)",
  },
  legendItem: {
    display: "flex",
    alignItems: "center",
    gap: "6px",
  },
  legendDot: {
    width: "10px",
    height: "10px",
    borderRadius: "50%",
  },
  rackMatrixContainer: {
    display: "flex",
    flexDirection: "column",
    gap: "25px",
  },
  matrixRow: {
    display: "grid",
    gridTemplateColumns: "100px 1fr",
    alignItems: "center",
    gap: "20px",
  },
  rowLabel: {
    fontSize: "0.95rem",
    fontWeight: 600,
    color: "var(--text-secondary)",
    textAlign: "left",
  },
  rowBays: {
    display: "grid",
    gridTemplateColumns: "repeat(4, 1fr)",
    gap: "15px",
  },
  bayCell: {
    background: "rgba(255, 255, 255, 0.01)",
    border: "1px solid rgba(255, 255, 255, 0.03)",
    borderRadius: "8px",
    padding: "10px",
  },
  bayLabel: {
    display: "block",
    fontSize: "0.75rem",
    color: "var(--text-muted)",
    textAlign: "left",
    marginBottom: "8px",
    fontWeight: 500,
  },
  binsFlex: {
    display: "flex",
    flexDirection: "column",
    gap: "8px",
  },
  binBox: {
    border: "1px solid var(--glass-border)",
    borderRadius: "6px",
    padding: "8px",
    cursor: "pointer",
    transition: "var(--transition-smooth)",
    textAlign: "left",
  },
  binBoxId: {
    fontSize: "0.75rem",
    fontWeight: 700,
    color: "var(--text-primary)",
    marginBottom: "4px",
  },
  barMiniContainer: {
    marginTop: "4px",
  },
  drawerBody: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
    textAlign: "left",
  },
  drawerStatRow: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "12px",
    fontSize: "0.9rem",
    color: "var(--text-secondary)",
  },
  drawerCapacitySection: {
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  capacityLabelRow: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.85rem",
    color: "var(--text-secondary)",
  },
  sectionHeader: {
    fontSize: "0.95rem",
    color: "var(--text-secondary)",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "8px",
    marginBottom: "12px",
  },
  skuList: {
    display: "flex",
    flexDirection: "column",
    gap: "10px",
    maxHeight: "35vh",
    overflowY: "auto",
    paddingRight: "4px",
  },
  skuItemCard: {
    background: "rgba(255, 255, 255, 0.02)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "12px",
  },
  skuCode: {
    fontSize: "0.85rem",
    fontWeight: 700,
    color: "var(--neon-blue)",
  },
  skuQty: {
    fontSize: "0.85rem",
    fontWeight: 600,
    color: "var(--text-primary)",
  },
  skuName: {
    fontSize: "0.8rem",
    color: "var(--text-secondary)",
    margin: "4px 0",
  },
  skuLot: {
    fontSize: "0.75rem",
    color: "var(--text-muted)",
  },
  emptySkuBox: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    background: "rgba(255,255,255,0.01)",
    border: "1px dashed var(--glass-border)",
    borderRadius: "8px",
    padding: "20px",
    color: "var(--text-muted)",
    fontSize: "0.85rem",
    justifyContent: "center",
  },
  drawerActions: {
    marginTop: "auto",
    paddingTop: "20px",
    borderTop: "1px solid var(--glass-border)",
  },
};

// Global hover style injecting
if (typeof document !== 'undefined') {
  const styleEl = document.createElement("style");
  styleEl.innerHTML += `
    .bin-grid-element:hover {
      border-color: var(--neon-blue) !important;
      transform: scale(1.02);
      box-shadow: 0 4px 12px rgba(0, 210, 255, 0.15);
    }
  `;
  document.head.appendChild(styleEl);
}
