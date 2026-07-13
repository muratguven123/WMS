import { useState, useEffect } from "react";
import { outboundService } from "../api/services";
import {
  Package,
  Truck,
  Printer,
  Barcode,
  CheckCircle,
  Plus,
  Trash2,
  AlertCircle,
  RefreshCw,
  Send,
  X
} from "lucide-react";
import { useI18n } from "../i18n/I18nContext";
import { DataTable } from "../components/DataTable";
import { fallbackCol } from "../components/tableUtils";

const SHIPPING_OUTBOUND_LIST_COLUMNS = [
  fallbackCol("id", "columns.common.id", 0, { dataType: "NUMBER" }),
  fallbackCol("order", "columns.shipping.order", 1),
  fallbackCol("status", "columns.common.status", 2),
  fallbackCol("carrier", "columns.shipping.carrier", 3),
];

interface PackedItem {
  id: number;
  sku: string;
  qty: number;
  weight: number;
  volume: number;
}

export const ShippingOutbound: React.FC<{ activeLocationId?: number | "" }> = ({ activeLocationId }) => {
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState<"packing" | "loading">("packing");
  const [_shipments, setShipments] = useState<{ id: number; shipmentNumber: string; status: string }[]>([]);

  useEffect(() => {
    if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) return;
    outboundService.listShipments().then((page) => setShipments(page.content)).catch(() => {});
  }, [activeLocationId]);
  
  // Packing States
  const [cartonItems, setCartonItems] = useState<PackedItem[]>([]);
  const [barcodeInput, setBarcodeInput] = useState("");
  const [showSsccModal, setShowSsccModal] = useState(false);
  const [latestSscc, setLatestSscc] = useState("");

  // Loading States
  const [truckChecklist, setTruckChecklist] = useState<Array<{ sscc: string; status: "PENDING" | "LOADED" }>>([
    { sscc: "SSCC-186900123456789012", status: "LOADED" },
    { sscc: "SSCC-186900123456789036", status: "PENDING" },
  ]);
  const [loadingInput, setLoadingInput] = useState("");
  const [isDispatched, setIsDispatched] = useState(false);
  const [outboxSyncing, setOutboxSyncing] = useState(false);
  const [outboxSynced, setOutboxSynced] = useState(false);

  // Specs for packaging simulation
  const cartonMaxVolume = 1.2; // m³
  const cartonMaxWeight = 40;  // kg

  const totalVol = cartonItems.reduce((sum, item) => sum + item.volume, 0);
  const totalWt = cartonItems.reduce((sum, item) => sum + item.weight, 0);

  const volPercent = Math.min(100, Math.round((totalVol / cartonMaxVolume) * 100));
  const wtPercent = Math.min(100, Math.round((totalWt / cartonMaxWeight) * 100));

  const handleAddItem = (e: React.FormEvent) => {
    e.preventDefault();
    if (!barcodeInput.trim()) return;

    // Simulate looking up barcode specs
    const code = barcodeInput.trim().toUpperCase();
    let newItem: PackedItem = { id: Date.now(), sku: code, qty: 1, weight: 1.5, volume: 0.04 };

    if (code.includes("MON")) {
      newItem = { id: Date.now(), sku: "SKU-MON-100", qty: 1, weight: 7.0, volume: 0.25 };
    } else if (code.includes("KEY")) {
      newItem = { id: Date.now(), sku: "SKU-KEY-500", qty: 1, weight: 1.2, volume: 0.03 };
    } else if (code.includes("CAB")) {
      newItem = { id: Date.now(), sku: "SKU-CAB-090", qty: 1, weight: 0.2, volume: 0.006 };
    } else if (code.includes("MOU")) {
      newItem = { id: Date.now(), sku: "SKU-MOU-400", qty: 1, weight: 0.3, volume: 0.01 };
    }

    // Check capacity
    if (totalVol + newItem.volume > cartonMaxVolume) {
      alert(t("ops.shipping.err.volume"));
      return;
    }
    if (totalWt + newItem.weight > cartonMaxWeight) {
      alert(t("ops.shipping.err.weight"));
      return;
    }

    setCartonItems(prev => {
      const existing = prev.find(item => item.sku === newItem.sku);
      if (existing) {
        return prev.map(item =>
          item.sku === newItem.sku
            ? { ...item, qty: item.qty + 1, weight: item.weight + newItem.weight, volume: item.volume + newItem.volume }
            : item
        );
      }
      return [...prev, newItem];
    });

    setBarcodeInput("");
  };

  const handleGenerateSSCC = () => {
    if (cartonItems.length === 0) {
      alert(t("ops.shipping.err.empty"));
      return;
    }
    // Generate valid looking SSCC barcode
    const randomDigits = Math.floor(100000000 + Math.random() * 900000000);
    const ssccCode = `SSCC-18690012${randomDigits}`;
    
    setLatestSscc(ssccCode);
    setShowSsccModal(true);

    // Register this new carton into truck load checklist
    setTruckChecklist(prev => [...prev, { sscc: ssccCode, status: "PENDING" }]);
  };

  const handleClearCarton = () => {
    setCartonItems([]);
  };

  const handleLoadCarton = (e: React.FormEvent) => {
    e.preventDefault();
    const code = loadingInput.trim();
    if (!code) return;

    const exists = truckChecklist.find(c => c.sscc === code);
    if (exists) {
      setTruckChecklist(prev =>
        prev.map(c => (c.sscc === code ? { ...c, status: "LOADED" } : c))
      );
      setLoadingInput("");
    } else {
      alert(`Okutulan barkod checklist üzerinde bulunamadı: ${code}`);
    }
  };

  const handleFinalDispatch = () => {
    const uncompleted = truckChecklist.some(c => c.status === "PENDING");
    if (uncompleted) {
      const confirm = window.confirm("Checklist'te yüklenmemiş koliler bulunmaktadır. Sevkiyatı yine de kapatmak istiyor musunuz?");
      if (!confirm) return;
    }

    setIsDispatched(true);
    setOutboxSyncing(true);
    setOutboxSynced(false);

    // Simulate async outbox message delivery to ERP
    setTimeout(() => {
      setOutboxSyncing(false);
      setOutboxSynced(true);
      console.log("Async outbox event published: SHIPMENT_COMPLETED_EVENT");
    }, 2000);
  };

  const resetLoadingScreen = () => {
    setIsDispatched(false);
    setOutboxSynced(false);
    setOutboxSyncing(false);
    setTruckChecklist([
      { sscc: "SSCC-186900123456789012", status: "LOADED" },
      { sscc: "SSCC-186900123456789036", status: "PENDING" },
    ]);
  };

  return (
    <div style={styles.container}>
      {/* View Tabbing Selector */}
      <div style={styles.tabHeader}>
        <button
          onClick={() => setActiveTab("packing")}
          style={{
            ...styles.tabBtn,
            borderColor: activeTab === "packing" ? "var(--neon-blue)" : "transparent",
            color: activeTab === "packing" ? "var(--neon-blue)" : "var(--text-secondary)",
            boxShadow: activeTab === "packing" ? "var(--shadow-neon-blue)" : "none",
          }}
        >
          <Package size={16} />
          {t("ops.shipping.tabPacking")}
        </button>
        <button
          onClick={() => setActiveTab("loading")}
          style={{
            ...styles.tabBtn,
            borderColor: activeTab === "loading" ? "var(--neon-green)" : "transparent",
            color: activeTab === "loading" ? "var(--neon-green)" : "var(--text-secondary)",
            boxShadow: activeTab === "loading" ? "var(--shadow-neon-green)" : "none",
          }}
        >
          <Truck size={16} />
          {t("ops.shipping.tabLoading")}
        </button>
      </div>

      {activeTab === "packing" ? (
        /* PACKING TAB CONTENT */
        <div className="layout-split">
          {/* Packaging Form & Items list */}
          <div className="glass-card" style={styles.tabCard}>
            <h3 style={styles.panelTitle}>{t("ops.shipping.packingStation")}</h3>

            <form onSubmit={handleAddItem} style={styles.barcodeForm}>
              <div className="form-group" style={{ flexGrow: 1, marginBottom: 0 }}>
                <input
                  type="text"
                  className="form-input"
                  placeholder={t("ops.shipping.scanPlaceholder")}
                  value={barcodeInput}
                  onChange={e => setBarcodeInput(e.target.value)}
                  style={{ width: "100%" }}
                />
              </div>
              <button type="submit" className="btn btn-primary">
                <Plus size={16} /> {t("common.add")}
              </button>
            </form>

            <div style={{ marginTop: "20px" }}>
              <h4 style={styles.tableTitle}>{t("ops.shipping.cartonContents")}</h4>
              <div style={{ maxHeight: "35vh", overflowY: "auto" }}>
                <DataTable
                  screenCode="SHIPPING_OUTBOUND_LIST"
                  rowKey={(item) => item.id}
                  rows={cartonItems}
                  fallbackColumns={SHIPPING_OUTBOUND_LIST_COLUMNS}
                  showColumnPicker={false}
                  emptyMessage={t("ops.shipping.emptyCarton")}
                  renderers={{
                    id: (item) => item.id,
                    order: (item) => <strong style={{ color: "var(--neon-blue)" }}>{item.sku}</strong>,
                    status: (item) => t("ops.shipping.qtyUnit").replace("{qty}", String(item.qty)),
                    carrier: (item) => `${item.weight.toFixed(1)} kg / ${item.volume.toFixed(3)} m³`,
                  }}
                />
              </div>
            </div>

            <div style={styles.packingActions}>
              <button className="btn btn-secondary" onClick={handleClearCarton} disabled={cartonItems.length === 0}>
                <Trash2 size={16} /> {t("ops.shipping.resetCarton")}
              </button>
              <button className="btn btn-success" onClick={handleGenerateSSCC} disabled={cartonItems.length === 0}>
                <Barcode size={16} /> {t("ops.shipping.closeCartonSscc")}
              </button>
            </div>
          </div>

          {/* Carton occupancy preview */}
          <div className="glass-card" style={styles.occupancyCard}>
            <h3 style={styles.panelTitle}>{t("ops.shipping.capacityGauge")}</h3>
            
            <div style={styles.capacityBody}>
              {/* Volume gauge */}
              <div style={styles.capMeter}>
                <div style={styles.capLabel}>
                  <span>{t("ops.shipping.totalVolume")}</span>
                  <span>{totalVol.toFixed(3)} / {cartonMaxVolume} m³</span>
                </div>
                <div className="capacity-bar-container" style={{ height: "12px" }}>
                  <div
                    className={`capacity-bar ${volPercent >= 90 ? "red" : volPercent >= 75 ? "orange" : "green"}`}
                    style={{ width: `${volPercent}%` }}
                  />
                </div>
                <span style={styles.capPercent}>{t("ops.shipping.percentFull").replace("{pct}", String(volPercent))}</span>
              </div>

              {/* Weight gauge */}
              <div style={styles.capMeter}>
                <div style={styles.capLabel}>
                  <span>{t("ops.shipping.totalWeight")}</span>
                  <span>{totalWt.toFixed(1)} / {cartonMaxWeight} kg</span>
                </div>
                <div className="capacity-bar-container" style={{ height: "12px" }}>
                  <div
                    className={`capacity-bar ${wtPercent >= 90 ? "red" : wtPercent >= 75 ? "orange" : "green"}`}
                    style={{ width: `${wtPercent}%` }}
                  />
                </div>
                <span style={styles.capPercent}>{t("ops.shipping.percentFull").replace("{pct}", String(wtPercent))}</span>
              </div>

              {/* Capacity Alerts */}
              {(volPercent > 80 || wtPercent > 80) && (
                <div style={styles.packAlert}>
                  <AlertCircle size={18} color="var(--neon-orange)" />
                  <span style={{ fontSize: "0.8rem", color: "var(--neon-orange)" }}>
                    {t("ops.shipping.capacityAlert")}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : (
        /* LOADING TAB CONTENT */
        <div className="layout-split">
          {/* Truck Loading scanning and Checklist */}
          <div className="glass-card" style={styles.tabCard}>
            <h3 style={styles.panelTitle}>Araç Yükleme Doğrulama</h3>

            {isDispatched ? (
              <div style={styles.dispatchedBox}>
                <CheckCircle size={48} color="var(--neon-green)" style={{ marginBottom: "15px" }} />
                <h3>Araç Sevkiyatı Tamamlandı!</h3>
                <p style={{ color: "var(--text-secondary)", margin: "10px 0" }}>
                  Yüklenen palet/koli verileri muhasebe ve ERP entegrasyon sistemine gönderildi.
                </p>
                
                {/* Outbox Badge Indicator */}
                <div style={{ display: "flex", gap: "10px", alignItems: "center", marginTop: "15px", justifyContent: "center" }}>
                  {outboxSyncing && (
                    <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "var(--neon-blue)" }}>
                      <RefreshCw size={16} className="spin-animation" style={{ animationDuration: "1s" }} />
                      <span>ERP Sırasına Gönderiliyor (Outbox)...</span>
                    </div>
                  )}
                  {outboxSynced && (
                    <span className="badge badge-green pulse-target" style={{ fontSize: "0.8rem", padding: "6px 12px" }}>
                      <Send size={12} /> sent to queue (ERP OK)
                    </span>
                  )}
                </div>

                <button className="btn btn-secondary" style={{ marginTop: "30px" }} onClick={resetLoadingScreen}>
                  Yeni Sevkiyat Başlat
                </button>
              </div>
            ) : (
              <>
                <form onSubmit={handleLoadCarton} style={styles.barcodeForm}>
                  <div className="form-group" style={{ flexGrow: 1, marginBottom: 0 }}>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="SSCC Koli Barkodu Okutun (örn: SSCC-18690012...)"
                      value={loadingInput}
                      onChange={e => setLoadingInput(e.target.value)}
                      style={{ width: "100%" }}
                    />
                  </div>
                  <button type="submit" className="btn btn-success">
                    Yükle
                  </button>
                </form>

                <div style={{ marginTop: "25px" }}>
                  <h4 style={styles.tableTitle}>Yükleme Kontrol Listesi</h4>
                  <div style={styles.checklistGrid}>
                    {truckChecklist.map((c, i) => (
                      <div
                        key={i}
                        style={{
                          ...styles.checkItem,
                          borderColor: c.status === "LOADED" ? "var(--neon-green)" : "rgba(255,255,255,0.08)",
                          background: c.status === "LOADED" ? "rgba(0, 245, 155, 0.05)" : "transparent"
                        }}
                      >
                        <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>{c.sscc}</span>
                        <span className={`badge ${c.status === "LOADED" ? "badge-green" : "badge-orange"}`}>
                          {c.status === "LOADED" ? "Yüklendi" : "Bekliyor"}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                <button
                  className="btn btn-primary"
                  style={{ width: "100%", marginTop: "30px" }}
                  onClick={handleFinalDispatch}
                >
                  Sevkiyatı Tamamla & Kapıyı Kapat
                </button>
              </>
            )}
          </div>

          {/* Shipment metadata */}
          <div className="glass-card" style={styles.occupancyCard}>
            <h3 style={styles.panelTitle}>Sevk Sipariş Detayı</h3>
            <div style={styles.shippingMeta}>
              <div style={styles.metaRow}><span>Plaka:</span> <strong>34 WMS 2026</strong></div>
              <div style={styles.metaRow}><span>Taşıyıcı Firma:</span> <span>DHL Freight Services</span></div>
              <div style={styles.metaRow}><span>Sevk Kapısı:</span> <span>GATE-03</span></div>
              <div style={styles.metaRow}><span>Yüklenen Koli:</span> <span>{truckChecklist.filter(c => c.status === "LOADED").length} / {truckChecklist.length}</span></div>
            </div>
          </div>
        </div>
      )}

      {/* PRINTABLE SSCC PREVIEW MODAL */}
      {showSsccModal && (
        <div className="modal-backdrop">
          <div className="glass-card modal-wrapper" style={styles.ssccModal}>
            <div style={styles.modalHeader}>
              <h3>SSCC Koli Etiketi Önizleme (Print Preview)</h3>
              <button className="close-btn" onClick={() => setShowSsccModal(false)}><X size={20} /></button>
            </div>
            
            {/* GS1 SSCC Label design */}
            <div style={styles.labelContainer} id="sscc-label-print">
              <div style={styles.labelHeader}>
                <span style={{ fontWeight: 800 }}>SSCC LOGISTICS LABEL</span>
                <span>GS1 STANDARD</span>
              </div>
              
              <div style={styles.labelBody}>
                <div style={styles.labelGrid}>
                  <div>
                    <span style={styles.lblLabel}>FROM:</span>
                    <strong style={styles.lblValue}> Apex Logistics Group</strong>
                    <div style={styles.lblDesc}>Istanbul East Hub, GATE-03</div>
                  </div>
                  <div>
                    <span style={styles.lblLabel}>TO:</span>
                    <strong style={styles.lblValue}> HORIZON RETAIL DEPT</strong>
                    <div style={styles.lblDesc}>Ankara Central Warehouse</div>
                  </div>
                </div>

                <div style={styles.labelDivider} />

                <div style={styles.labelGrid3}>
                  <div>
                    <span style={styles.lblLabel}>CONTENT COUNT:</span>
                    <strong style={styles.lblValue}> {cartonItems.reduce((s, i) => s + i.qty, 0)} Pcs</strong>
                  </div>
                  <div>
                    <span style={styles.lblLabel}>NET WEIGHT:</span>
                    <strong style={styles.lblValue}> {totalWt.toFixed(1)} Kg</strong>
                  </div>
                  <div>
                    <span style={styles.lblLabel}>VOLUME:</span>
                    <strong style={styles.lblValue}> {totalVol.toFixed(3)} M³</strong>
                  </div>
                </div>

                <div style={styles.labelDivider} />

                {/* Simulated Barcode */}
                <div style={styles.barcodeZone}>
                  <div style={styles.gs1Text}>SSCC (SERIAL SHIPPING CONTAINER CODE)</div>
                  <div style={styles.barcodeBars}>
                    {/* Simulated vertical stripes */}
                    <div style={{ ...styles.barStripe, width: "3px" }} />
                    <div style={{ ...styles.barStripe, width: "1px" }} />
                    <div style={{ ...styles.barStripe, width: "4px" }} />
                    <div style={{ ...styles.barStripe, width: "2px" }} />
                    <div style={{ ...styles.barStripe, width: "1px" }} />
                    <div style={{ ...styles.barStripe, width: "3px" }} />
                    <div style={{ ...styles.barStripe, width: "2px" }} />
                    <div style={{ ...styles.barStripe, width: "4px" }} />
                    <div style={{ ...styles.barStripe, width: "1px" }} />
                    <div style={{ ...styles.barStripe, width: "3px" }} />
                    <div style={{ ...styles.barStripe, width: "2px" }} />
                    <div style={{ ...styles.barStripe, width: "1px" }} />
                    <div style={{ ...styles.barStripe, width: "4px" }} />
                    <div style={{ ...styles.barStripe, width: "1px" }} />
                    <div style={{ ...styles.barStripe, width: "3px" }} />
                  </div>
                  <div style={styles.barcodeDigits}>
                    (00) 3 8690012 {latestSscc.split("-")[1] || "345678901"} 8
                  </div>
                </div>
              </div>
            </div>

            <div style={styles.modalFooter}>
              <button className="btn btn-secondary" onClick={() => setShowSsccModal(false)}>
                Kapat
              </button>
              <button className="btn btn-primary" onClick={() => { alert("Yazıcıya gönderiliyor..."); setShowSsccModal(false); handleClearCarton(); }}>
                <Printer size={16} /> Etiketi Yazdır (Print)
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
  },
  tabHeader: {
    display: "flex",
    gap: "10px",
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
  tabCard: {
    padding: "20px",
    display: "flex",
    flexDirection: "column",
    maxHeight: "75vh",
  },
  panelTitle: {
    fontSize: "1.1rem",
    fontWeight: 600,
    color: "var(--text-primary)",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "10px",
    marginBottom: "15px",
    textAlign: "left",
  },
  barcodeForm: {
    display: "flex",
    gap: "12px",
    width: "100%",
  },
  tableTitle: {
    fontSize: "0.9rem",
    color: "var(--text-secondary)",
    textAlign: "left",
    marginBottom: "8px",
  },
  packingActions: {
    display: "flex",
    justifyContent: "space-between",
    marginTop: "auto",
    paddingTop: "20px",
    borderTop: "1px solid var(--glass-border)",
  },
  occupancyCard: {
    padding: "25px",
    display: "flex",
    flexDirection: "column",
  },
  capacityBody: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
  },
  capMeter: {
    display: "flex",
    flexDirection: "column",
    gap: "6px",
    textAlign: "left",
  },
  capLabel: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.85rem",
    color: "var(--text-secondary)",
  },
  capPercent: {
    fontSize: "0.75rem",
    color: "var(--text-muted)",
    textAlign: "right",
  },
  packAlert: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    background: "rgba(255, 159, 0, 0.08)",
    border: "1px solid rgba(255, 159, 0, 0.2)",
    borderRadius: "8px",
    padding: "10px",
    textAlign: "left",
  },
  dispatchedBox: {
    padding: "40px 10px",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    textAlign: "center",
  },
  checklistGrid: {
    display: "flex",
    flexDirection: "column",
    gap: "10px",
  },
  checkItem: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "12px 16px",
    transition: "var(--transition-smooth)",
  },
  shippingMeta: {
    display: "flex",
    flexDirection: "column",
    gap: "12px",
    background: "rgba(255,255,255,0.01)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "15px",
    textAlign: "left",
  },
  metaRow: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.85rem",
    color: "var(--text-secondary)",
  },
  ssccModal: {
    width: "420px",
    maxWidth: "90%",
    background: "#fff !important", // White background for printing layout
    color: "#000 !important",
    border: "1px solid #000 !important",
  },
  modalHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottom: "1px solid #ddd",
    paddingBottom: "10px",
    marginBottom: "15px",
  },
  labelContainer: {
    fontFamily: "monospace",
    border: "2px solid #000",
    padding: "15px",
    background: "#fff",
    color: "#000",
    textAlign: "left",
  },
  labelHeader: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.75rem",
    borderBottom: "2px solid #000",
    paddingBottom: "4px",
    marginBottom: "10px",
  },
  labelBody: {
    display: "flex",
    flexDirection: "column",
  },
  labelGrid: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: "10px",
    fontSize: "0.7rem",
  },
  lblLabel: {
    display: "block",
    fontSize: "0.6rem",
    color: "#555",
  },
  lblValue: {
    fontSize: "0.75rem",
  },
  lblDesc: {
    fontSize: "0.65rem",
    color: "#666",
  },
  labelDivider: {
    borderBottom: "1px solid #000",
    margin: "10px 0",
  },
  labelGrid3: {
    display: "grid",
    gridTemplateColumns: "repeat(3, 1fr)",
    gap: "5px",
    fontSize: "0.7rem",
  },
  barcodeZone: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    marginTop: "15px",
  },
  gs1Text: {
    fontSize: "0.6rem",
    fontWeight: "bold",
    marginBottom: "8px",
  },
  barcodeBars: {
    display: "flex",
    alignItems: "stretch",
    height: "55px",
    width: "100%",
    justifyContent: "center",
    background: "#000",
    padding: "0 10px",
  },
  barStripe: {
    background: "#fff",
    marginRight: "2px",
  },
  barcodeDigits: {
    marginTop: "4px",
    fontSize: "0.8rem",
    fontWeight: "bold",
    letterSpacing: "2px",
  },
  modalFooter: {
    display: "flex",
    justifyContent: "flex-end",
    gap: "10px",
    marginTop: "20px",
    borderTop: "1px solid #ddd",
    paddingTop: "15px",
  },
};

// Add rotation animation for outbox sync
if (typeof document !== 'undefined') {
  const styleEl = document.createElement("style");
  styleEl.innerHTML += `
    @keyframes rotateAnim {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    .spin-animation {
      animation: rotateAnim 1.2s infinite linear;
    }
  `;
  document.head.appendChild(styleEl);
}
