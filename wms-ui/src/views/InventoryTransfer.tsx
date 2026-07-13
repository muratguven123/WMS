import { useState, useEffect } from "react";
import {
  ArrowLeftRight,
  ArrowRight,
  AlertTriangle,
  Search,
  Database,
  Move
} from "lucide-react";
import { inventoryService } from "../api/services";
import { tenantContext } from "../api/wms-api-client";
import { stompDestinations } from "../realtime/destinations";
import { useTopicSubscription } from "../realtime/StompProvider";
import { MOCK_LOCATIONS_DATA } from "./RackView";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";

const INVENTORY_TRANSFER_LIST_COLUMNS = [
  fallbackCol("sku", "columns.inventory.sku", 0),
  fallbackCol("from", "columns.inventory.from", 1),
  fallbackCol("to", "columns.inventory.to", 2),
  fallbackCol("qty", "columns.common.qty", 3, { dataType: "NUMBER" }),
];

interface StockItem {
  id: string;
  sku: string;
  name: string;
  lot: string;
  binId: string;
  qty: number;
  // reference details
  volumePerQty: number;
  weightPerQty: number;
}

export const InventoryTransfer: React.FC<{ activeLocationId?: number | "" }> = ({ activeLocationId }) => {
  const { t } = useI18n();
  const [stock, setStock] = useState<StockItem[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  
  // Form state
  const [selectedSkuLotBin, setSelectedSkuLotBin] = useState("");
  const [targetBinId, setTargetBinId] = useState("");
  const [transferQty, setTransferQty] = useState<number>(0);

  // Drag state
  const [draggedItem, setDraggedItem] = useState<StockItem | null>(null);

  // Load stock dynamically from global shared locations
  const loadStockFromGlobal = () => {
    const list: StockItem[] = [];
    MOCK_LOCATIONS_DATA.forEach((loc) => {
      loc.skus.forEach((sku) => {
        // Approximate specs for weight/volume per item
        let volPer = 0.05;
        let wtPer = 1;
        if (sku.sku === "SKU-MON-100") { volPer = 0.25; wtPer = 7; }
        else if (sku.sku === "SKU-KEY-500") { volPer = 0.03; wtPer = 1.2; }
        else if (sku.sku === "SKU-CAB-090") { volPer = 0.006; wtPer = 0.2; }
        else if (sku.sku === "SKU-MOU-400") { volPer = 0.01; wtPer = 0.3; }

        list.push({
          id: `${sku.sku}-${sku.lot}-${loc.code}`,
          sku: sku.sku,
          name: sku.name,
          lot: sku.lot,
          binId: loc.code,
          qty: sku.qty,
          volumePerQty: volPer,
          weightPerQty: wtPer
        });
      });
    });
    setStock(list);
  };

  useEffect(() => {
    loadStockFromGlobal();
    if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) return;
    inventoryService.getStocks().then((data) => {
      if (!data.stocks?.length) return;
      const list: StockItem[] = data.stocks.map((s) => ({
        id: `${s.sku}-${s.id}`,
        sku: s.sku,
        name: s.sku,
        lot: s.lot ?? "—",
        binId: s.binId ?? "—",
        qty: s.quantity,
        volumePerQty: 0.05,
        weightPerQty: 1,
      }));
      setStock(list);
    }).catch(() => {});
  }, [activeLocationId]);

  const companyId = tenantContext.getCompanyId();
  const warehouseId = activeLocationId || tenantContext.getLocationId();
  useTopicSubscription(
    companyId && warehouseId ? stompDestinations.stock(companyId, warehouseId) : null,
    (event) => {
      const p = event.payload;
      const sku = String(p.productCode ?? p.skuId ?? "");
      const binId = String(p.binId ?? "");
      const qty = Number(p.qty ?? 0);
      if (!sku) return;
      setStock((prev) => {
        const idx = prev.findIndex((s) => s.sku === sku && s.binId === binId);
        if (idx >= 0) {
          const next = [...prev];
          next[idx] = { ...next[idx], qty: qty > 0 ? qty : Math.max(0, next[idx].qty + Number(p.delta ?? 0)) };
          return next.filter((s) => s.qty > 0);
        }
        if (qty > 0 && binId) {
          return [...prev, { id: `${sku}-${binId}`, sku, name: sku, lot: "LIVE", binId, qty, volumePerQty: 0.05, weightPerQty: 1 }];
        }
        return prev;
      });
      loadStockFromGlobal();
    },
  );

  // Filtered stock list
  const filteredStock = stock.filter(
    (item) =>
      item.sku.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.binId.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // Selected source item details
  const activeSourceItem = stock.find((s) => s.id === selectedSkuLotBin);
  // Selected target bin details
  const activeTargetBin = MOCK_LOCATIONS_DATA.find((l) => l.code === targetBinId);

  // Compute live capacity previews
  let targetCurrentVolPercent = 0;
  let targetProjectedVolPercent = 0;
  let targetCurrentWtPercent = 0;
  let targetProjectedWtPercent = 0;
  let isTargetOverloaded = false;
  let isTargetNearLimit = false;

  if (activeTargetBin) {
    const maxV = activeTargetBin.maxVolume;
    const maxW = activeTargetBin.maxWeight;

    // Current percents
    targetCurrentVolPercent = Math.min(100, Math.round((activeTargetBin.volumeOccupied / maxV) * 100));
    targetCurrentWtPercent = Math.min(100, Math.round((activeTargetBin.weightOccupied / maxW) * 100));

    if (activeSourceItem && transferQty > 0) {
      const addedVol = transferQty * activeSourceItem.volumePerQty;
      const addedWt = transferQty * activeSourceItem.weightPerQty;

      const projectedVol = activeTargetBin.volumeOccupied + addedVol;
      const projectedWt = activeTargetBin.weightOccupied + addedWt;

      targetProjectedVolPercent = Math.min(100, Math.round((projectedVol / maxV) * 100));
      targetProjectedWtPercent = Math.min(100, Math.round((projectedWt / maxW) * 100));

      if (projectedVol > maxV || projectedWt > maxW) {
        isTargetOverloaded = true;
      } else if (projectedVol / maxV > 0.8 || projectedWt / maxW > 0.8) {
        isTargetNearLimit = true;
      }
    }
  }

  // Handle Drag Start
  const handleDragStart = (e: React.DragEvent, item: StockItem) => {
    setDraggedItem(item);
    e.dataTransfer.setData("text/plain", item.id);
  };

  // Handle Drag Over
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault(); // Required to allow drop
  };

  // Handle Drop on Target Bin Card
  const handleDrop = (e: React.DragEvent, binId: string) => {
    e.preventDefault();
    const itemId = e.dataTransfer.getData("text/plain");
    const item = stock.find((s) => s.id === itemId);
    
    if (item) {
      if (item.binId === binId) {
        alert(t("ops.transfer.err.sameBin"));
        return;
      }
      setSelectedSkuLotBin(item.id);
      setTargetBinId(binId);
      setTransferQty(Math.min(item.qty, 10)); // Default suggest 10 or max qty
      console.log(`Drag & Drop filled transfer: SKU=${item.sku} from ${item.binId} to ${binId}`);
    }
  };

  // Submit transfer handler
  const handleExecuteTransfer = (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeSourceItem || !activeTargetBin || transferQty <= 0) {
      alert(t("ops.transfer.err.fillAll"));
      return;
    }

    if (transferQty > activeSourceItem.qty) {
      alert(t("ops.transfer.err.qty"));
      return;
    }

    if (activeSourceItem.binId === targetBinId) {
      alert(t("ops.transfer.err.sameBin"));
      return;
    }

    // Capacity checks
    const addedVol = transferQty * activeSourceItem.volumePerQty;
    const addedWt = transferQty * activeSourceItem.weightPerQty;

    const sourceBin = MOCK_LOCATIONS_DATA.find((l) => l.code === activeSourceItem.binId)!;

    if (activeTargetBin.volumeOccupied + addedVol > activeTargetBin.maxVolume) {
      alert(t("ops.transfer.err.volume"));
      return;
    }
    if (activeTargetBin.weightOccupied + addedWt > activeTargetBin.maxWeight) {
      alert(t("ops.transfer.err.weight"));
      return;
    }
    if (activeTargetBin.status === "BLOCKED") {
      alert(t("ops.transfer.err.blocked"));
      return;
    }

    // Execute in memory
    // 1) Subtract from Source
    sourceBin.volumeOccupied = Number(Math.max(0, sourceBin.volumeOccupied - addedVol).toFixed(2));
    sourceBin.weightOccupied = Math.max(0, sourceBin.weightOccupied - addedWt);
    const srcSku = sourceBin.skus.find((s) => s.sku === activeSourceItem.sku && s.lot === activeSourceItem.lot)!;
    srcSku.qty -= transferQty;
    if (srcSku.qty <= 0) {
      sourceBin.skus = sourceBin.skus.filter((s) => s !== srcSku);
    }
    // Update source status if it was FULL
    if (sourceBin.status === "FULL") {
      sourceBin.status = "ACTIVE";
    }

    // 2) Add to Target
    activeTargetBin.volumeOccupied = Number((activeTargetBin.volumeOccupied + addedVol).toFixed(2));
    activeTargetBin.weightOccupied += addedWt;
    const tarSku = activeTargetBin.skus.find((s) => s.sku === activeSourceItem.sku && s.lot === activeSourceItem.lot);
    if (tarSku) {
      tarSku.qty += transferQty;
    } else {
      activeTargetBin.skus.push({
        sku: activeSourceItem.sku,
        name: activeSourceItem.name,
        qty: transferQty,
        lot: activeSourceItem.lot
      });
    }

    // Check if target is full now
    if (activeTargetBin.volumeOccupied >= activeTargetBin.maxVolume || activeTargetBin.weightOccupied >= activeTargetBin.maxWeight) {
      activeTargetBin.status = "FULL";
    }

    alert(t("ops.transfer.success").replace("{qty}", String(transferQty)).replace("{sku}", activeSourceItem.sku));
    
    // Reset Form
    setSelectedSkuLotBin("");
    setTargetBinId("");
    setTransferQty(0);
    
    // Refresh local stock list
    loadStockFromGlobal();
  };

  return (
    <div style={styles.container}>
      {/* Search Bar */}
      <div className="glass-card" style={styles.searchBar}>
        <Search size={18} color="var(--text-secondary)" />
        <input
          type="text"
          placeholder={t("ops.transfer.search")}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={styles.searchInput}
        />
      </div>

      <div className="layout-split">
        {/* Left Side: On-Hand Inventory Table (Draggable rows) */}
        <div className="glass-card" style={styles.tableCard}>
          <div style={styles.cardHeader}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <Database size={20} color="var(--neon-blue)" />
              <h3 style={{ fontSize: "1.1rem" }}>{t("ops.transfer.inventoryTitle")}</h3>
            </div>
            <span style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>{t("ops.transfer.dragHint")}</span>
          </div>

          <div style={{ maxHeight: "65vh", overflowY: "auto" }}>
            <DataTable
              screenCode="INVENTORY_TRANSFER_LIST"
              rowKey={(item) => item.id}
              rows={filteredStock}
              fallbackColumns={INVENTORY_TRANSFER_LIST_COLUMNS}
              emptyMessage={t("ops.transfer.noStock")}
              rowProps={(item) => ({
                draggable: true,
                onDragStart: (e) => handleDragStart(e, item),
                style: styles.draggableRow,
                title: t("ops.transfer.dragRowTitle"),
              })}
              renderers={{
                sku: (item) => <strong style={{ color: "var(--neon-blue)" }}>{item.sku}</strong>,
                from: (item) => <strong style={{ color: "var(--neon-green)" }}>{item.binId}</strong>,
                to: () => "—",
                qty: (item) => <strong>{item.qty}</strong>,
              }}
            />
          </div>
        </div>

        {/* Right Side: Transfer Form & Capacity Meter */}
        <div style={styles.formSplit}>
          {/* Transfer Form */}
          <div className="glass-card" style={styles.formCard}>
            <h3 style={styles.formHeaderTitle}>{t("ops.transfer.title")}</h3>
            
            <form onSubmit={handleExecuteTransfer} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              
              {/* SKU SELECT */}
              <div className="form-group">
                <label>{t("ops.transfer.sourceLabel")}</label>
                <select
                  className="form-select"
                  value={selectedSkuLotBin}
                  onChange={(e) => setSelectedSkuLotBin(e.target.value)}
                  required
                >
                  <option value="">{t("ops.transfer.sourcePlaceholder")}</option>
                  {stock.map((item) => (
                    <option key={item.id} value={item.id}>
                      {t("ops.transfer.sourceOption")
                        .replace("{sku}", item.sku)
                        .replace("{lot}", item.lot)
                        .replace("{bin}", item.binId)
                        .replace("{qty}", String(item.qty))}
                    </option>
                  ))}
                </select>
              </div>

              {/* TARGET BIN SELECT */}
              <div className="form-group">
                <label>{t("ops.transfer.targetBin")}</label>
                <select
                  className="form-select"
                  value={targetBinId}
                  onChange={(e) => setTargetBinId(e.target.value)}
                  required
                >
                  <option value="">{t("ops.transfer.targetPlaceholder")}</option>
                  {MOCK_LOCATIONS_DATA.map((loc) => (
                    <option key={loc.id} value={loc.code} disabled={loc.status === "BLOCKED"}>
                      {loc.code} {loc.status === "BLOCKED" ? `(${t("ops.transfer.statusBlocked")})` : ""} ({loc.status === "FULL" ? t("ops.transfer.statusFull") : t("ops.transfer.statusActive")})
                    </option>
                  ))}
                </select>
              </div>

              {/* QUANTITY */}
              <div className="form-group">
                <label>{t("ops.transfer.qtyLabel")}</label>
                <input
                  type="number"
                  className="form-input"
                  min={1}
                  max={activeSourceItem ? activeSourceItem.qty : undefined}
                  value={transferQty || ""}
                  onChange={(e) => setTransferQty(parseInt(e.target.value) || 0)}
                  placeholder={activeSourceItem ? t("ops.transfer.qtyMax").replace("{qty}", String(activeSourceItem.qty)) : t("ops.transfer.qtyPlaceholder")}
                  required
                />
              </div>

              <button
                type="submit"
                className="btn btn-primary"
                style={{ marginTop: "10px" }}
                disabled={isTargetOverloaded || !selectedSkuLotBin || !targetBinId || transferQty <= 0}
              >
                <ArrowLeftRight size={16} /> {t("ops.transfer.execute")}
              </button>
            </form>
          </div>

          {/* D&D Drop Zone and Capacity Preview */}
          <div
            className="glass-card"
            onDragOver={handleDragOver}
            onDrop={(e) => handleDrop(e, targetBinId || "A-01-01-01")}
            style={{
              ...styles.capacityCard,
              border: draggedItem ? "2px dashed var(--neon-blue)" : "1px solid var(--glass-border)",
              boxShadow: isTargetOverloaded 
                ? "var(--shadow-neon-red)" 
                : isTargetNearLimit 
                  ? "var(--shadow-neon-orange)" 
                  : "none"
            }}
          >
            <h4 style={styles.formHeaderTitle}>{t("ops.transfer.capacityTitle")}</h4>
            {draggedItem && (
              <div style={styles.dragAlert}>
                <Move size={16} className="pulse-route" style={{ animationDuration: "1s" }} />
                <span>{t("ops.transfer.dragDropAlert")}</span>
              </div>
            )}

            {activeTargetBin ? (
              <div style={styles.capacityBody}>
                <div style={styles.targetInfo}>
                  <strong style={{ color: "var(--neon-green)" }}>{t("ops.transfer.binAddress").replace("{code}", activeTargetBin.code)}</strong>
                  <span className={`badge ${
                    activeTargetBin.status === "ACTIVE" 
                      ? "badge-green" 
                      : activeTargetBin.status === "BLOCKED" 
                        ? "badge-orange" 
                        : "badge-red"
                  }`}>
                    {activeTargetBin.status}
                  </span>
                </div>

                {/* VOLUME CHART */}
                <div style={styles.capBarGroup}>
                  <div style={styles.capHeader}>
                    <span>{t("ops.transfer.volumeProjected")}</span>
                    <span>
                      {activeTargetBin.volumeOccupied} ➔ &nbsp;
                      <strong style={{ color: isTargetOverloaded ? "var(--neon-red)" : "var(--neon-blue)" }}>
                        {(activeTargetBin.volumeOccupied + (transferQty * (activeSourceItem?.volumePerQty || 0))).toFixed(2)}
                      </strong> / {activeTargetBin.maxVolume} m³
                    </span>
                  </div>
                  <div className="capacity-bar-container">
                    {/* Current */}
                    <div
                      className="capacity-bar green"
                      style={{ width: `${targetCurrentVolPercent}%`, position: "absolute", zIndex: 2 }}
                    />
                    {/* Projected increase */}
                    {targetProjectedVolPercent > targetCurrentVolPercent && (
                      <div
                        className="capacity-bar"
                        style={{
                          width: `${targetProjectedVolPercent}%`,
                          backgroundColor: isTargetOverloaded 
                            ? "var(--neon-red)" 
                            : isTargetNearLimit 
                              ? "var(--neon-orange)" 
                              : "var(--neon-blue)",
                          position: "absolute",
                          zIndex: 1
                        }}
                      />
                    )}
                  </div>
                </div>

                {/* WEIGHT CHART */}
                <div style={styles.capBarGroup}>
                  <div style={styles.capHeader}>
                    <span>{t("ops.transfer.weightProjected")}</span>
                    <span>
                      {activeTargetBin.weightOccupied} ➔ &nbsp;
                      <strong style={{ color: isTargetOverloaded ? "var(--neon-red)" : "var(--neon-blue)" }}>
                        {activeTargetBin.weightOccupied + (transferQty * (activeSourceItem?.weightPerQty || 0))}
                      </strong> / {activeTargetBin.maxWeight} kg
                    </span>
                  </div>
                  <div className="capacity-bar-container">
                    {/* Current */}
                    <div
                      className="capacity-bar green"
                      style={{ width: `${targetCurrentWtPercent}%`, position: "absolute", zIndex: 2 }}
                    />
                    {/* Projected increase */}
                    {targetProjectedWtPercent > targetCurrentWtPercent && (
                      <div
                        className="capacity-bar"
                        style={{
                          width: `${targetProjectedWtPercent}%`,
                          backgroundColor: isTargetOverloaded 
                            ? "var(--neon-red)" 
                            : isTargetNearLimit 
                              ? "var(--neon-orange)" 
                              : "var(--neon-blue)",
                          position: "absolute",
                          zIndex: 1
                        }}
                      />
                    )}
                  </div>
                </div>

                {/* WARNING MESSAGES */}
                {isTargetOverloaded && (
                  <div style={styles.warningBoxRed}>
                    <AlertTriangle size={18} />
                    <div>
                      <strong>{t("ops.transfer.capacityOverload")}</strong>
                      <p style={{ fontSize: "0.75rem", marginTop: "2px" }}>{t("ops.transfer.capacityOverloadDesc")}</p>
                    </div>
                  </div>
                )}

                {isTargetNearLimit && !isTargetOverloaded && (
                  <div style={styles.warningBoxOrange}>
                    <AlertTriangle size={18} />
                    <div>
                      <strong>{t("ops.transfer.capacityNearLimit")}</strong>
                      <p style={{ fontSize: "0.75rem", marginTop: "2px" }}>{t("ops.transfer.capacityNearLimitDesc")}</p>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div style={styles.emptyFormMsg}>
                <ArrowRight size={24} color="var(--text-muted)" style={{ marginBottom: "8px" }} />
                <p>{t("ops.transfer.selectTargetBin")}</p>
              </div>
            )}
          </div>
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
  searchBar: {
    display: "flex",
    alignItems: "center",
    padding: "10px 20px",
    gap: "12px",
  },
  searchInput: {
    background: "transparent",
    border: "none",
    color: "var(--text-primary)",
    fontFamily: "var(--font-sans)",
    outline: "none",
    width: "100%",
    fontSize: "0.95rem",
  },
  tableCard: {
    padding: "20px",
    maxHeight: "75vh",
  },
  cardHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "15px",
    marginBottom: "10px",
  },
  draggableRow: {
    cursor: "grab",
    userSelect: "none",
  },
  formSplit: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
  },
  formCard: {
    padding: "25px",
    textAlign: "left",
  },
  formHeaderTitle: {
    fontSize: "1.05rem",
    fontWeight: 600,
    marginBottom: "15px",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "10px",
    color: "var(--text-primary)",
    textAlign: "left",
  },
  capacityCard: {
    padding: "25px",
    flexGrow: 1,
    display: "flex",
    flexDirection: "column",
    textAlign: "left",
    transition: "var(--transition-smooth)",
  },
  capacityBody: {
    display: "flex",
    flexDirection: "column",
    gap: "18px",
  },
  targetInfo: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
  },
  capBarGroup: {
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  capHeader: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.8rem",
    color: "var(--text-secondary)",
  },
  dragAlert: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    background: "rgba(0, 210, 255, 0.08)",
    border: "1px solid rgba(0, 210, 255, 0.2)",
    color: "var(--neon-blue)",
    borderRadius: "8px",
    padding: "10px",
    fontSize: "0.8rem",
    marginBottom: "15px",
  },
  warningBoxRed: {
    display: "flex",
    alignItems: "flex-start",
    gap: "10px",
    background: "rgba(255, 51, 102, 0.1)",
    border: "1px solid rgba(255, 51, 102, 0.3)",
    color: "var(--neon-red)",
    borderRadius: "8px",
    padding: "12px",
    boxShadow: "var(--shadow-neon-red)",
  },
  warningBoxOrange: {
    display: "flex",
    alignItems: "flex-start",
    gap: "10px",
    background: "rgba(255, 159, 0, 0.1)",
    border: "1px solid rgba(255, 159, 0, 0.3)",
    color: "var(--neon-orange)",
    borderRadius: "8px",
    padding: "12px",
    boxShadow: "var(--shadow-neon-orange)",
  },
  emptyFormMsg: {
    flexGrow: 1,
    display: "flex",
    flexDirection: "column",
    justifyContent: "center",
    alignItems: "center",
    textAlign: "center",
    color: "var(--text-muted)",
    fontSize: "0.85rem",
    padding: "20px 0",
  },
};

// Global style injection
if (typeof document !== 'undefined') {
  const styleEl = document.createElement("style");
  styleEl.innerHTML += `
    tr[draggable="true"]:hover {
      background: rgba(0, 210, 255, 0.05) !important;
      border-color: var(--neon-blue);
    }
  `;
  document.head.appendChild(styleEl);
}
