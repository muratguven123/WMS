import { useEffect, useState } from "react";
import { inboundService, type ReceiptResponse } from "../api/services";
import {
  Inbox,
  ClipboardCheck,
  MapPin,
  PlayCircle,
  Map,
  CheckCircle2,
  Package,
  X
} from "lucide-react";
import { MOCK_LOCATIONS_DATA } from "./RackView";
import { useI18n } from "../i18n/I18nContext";

export interface InboundReceipt {
  id: number;
  supplier: string;
  sku: string;
  skuName: string;
  qty: number;
  lot: string;
  weight: number; // kg
  volume: number; // m³
  status: "PENDING_QC" | "QC_APPROVED" | "COMPLETED";
  suggestedBin?: string;
}

const INITIAL_RECEIPTS: InboundReceipt[] = [
  {
    id: 101,
    supplier: "Nexus Tech Electronics",
    sku: "SKU-MON-100",
    skuName: "Premium LED Monitor 27\"",
    qty: 20,
    lot: "L-2026-A1",
    weight: 140,
    volume: 5.0,
    status: "PENDING_QC",
    suggestedBin: "A-02-02-01",
  },
  {
    id: 102,
    supplier: "LogiAccess Global",
    sku: "SKU-KEY-500",
    skuName: "Mechanical Keyboard RGB",
    qty: 50,
    lot: "L-2026-A2",
    weight: 60,
    volume: 1.5,
    status: "PENDING_QC",
    suggestedBin: "B-03-01-02",
  },
  {
    id: 103,
    supplier: "Volta Cable Co.",
    sku: "SKU-CAB-090",
    skuName: "USB-C Braided Cable 2m",
    qty: 120,
    lot: "L-2026-A3",
    weight: 24,
    volume: 0.8,
    status: "QC_APPROVED",
    suggestedBin: "A-01-01-01",
  },
  {
    id: 104,
    supplier: "Orbit Accessories",
    sku: "SKU-MOU-400",
    skuName: "Wireless Ergonomic Mouse",
    qty: 40,
    lot: "L-2026-A4",
    weight: 12,
    volume: 0.4,
    status: "COMPLETED",
    suggestedBin: "C-02-02-02",
  },
];

interface InboundPutawayProps {
  onNavigateToMap: (binId: string) => void;
  activeLocationId?: number | "";
}

function mapApiReceipt(r: ReceiptResponse): InboundReceipt {
  const item = r.items[0];
  const statusMap: Record<string, InboundReceipt["status"]> = {
    QC_PENDING: "PENDING_QC",
    QC_APPROVED: "QC_APPROVED",
    APPROVED: "QC_APPROVED",
    COMPLETED: "COMPLETED",
  };
  return {
    id: r.id,
    supplier: String(r.inboundOrderId),
    sku: item?.productCode ?? "—",
    skuName: item?.productCode ?? "—",
    qty: Number(item?.quantity ?? item?.expectedQuantity ?? 0),
    lot: item?.lotNumber ?? "",
    weight: 10,
    volume: 1,
    status: statusMap[r.status] ?? "PENDING_QC",
  };
}

export const InboundPutaway: React.FC<InboundPutawayProps> = ({ onNavigateToMap, activeLocationId }) => {
  const { t } = useI18n();
  const [receipts, setReceipts] = useState<InboundReceipt[]>(INITIAL_RECEIPTS);
  const [selectedReceipt, setSelectedReceipt] = useState<InboundReceipt | null>(null);
  
  // QC Checklist Modal State
  const [showQcModal, setShowQcModal] = useState(false);
  const [qcChecklist, setQcChecklist] = useState({
    packagingOk: false,
    quantityOk: false,
    tempControlOk: false,
    labelingOk: false,
  });

  // Putaway Recommendation State
  const [engineRecommendation, setEngineRecommendation] = useState<{
    binId: string;
    score: number;
    reason: string;
  } | null>(null);

  useEffect(() => {
    if ((window as unknown as { wmsOfflineMode?: boolean }).wmsOfflineMode) return;
    inboundService
      .listReceipts()
      .then((page) => {
        if (page.content.length > 0) {
          setReceipts(page.content.map(mapApiReceipt));
        }
      })
      .catch(() => { /* mock fallback */ });
  }, [activeLocationId]);
  const [isEngineCalculating, setIsEngineCalculating] = useState(false);

  const handleOpenQC = (receipt: InboundReceipt) => {
    setSelectedReceipt(receipt);
    setQcChecklist({
      packagingOk: false,
      quantityOk: false,
      tempControlOk: false,
      labelingOk: false,
    });
    setShowQcModal(true);
  };

  const handleQcSubmit = () => {
    if (!selectedReceipt) return;
    
    const isSuccess = Object.values(qcChecklist).every(Boolean);
    if (!isSuccess) {
      alert("QC KONTROLÜ BAŞARISIZ! Tüm kontrol kutuları doğrulanmalıdır.");
      return;
    }

    setReceipts(prev =>
      prev.map(r => (r.id === selectedReceipt.id ? { ...r, status: "QC_APPROVED" } : r))
    );
    setShowQcModal(false);
    setSelectedReceipt(prev => prev ? { ...prev, status: "QC_APPROVED" } : null);
  };

  const handleGetSuggestion = (receipt: InboundReceipt) => {
    setIsEngineCalculating(true);
    setEngineRecommendation(null);
    
    // Simulate Directed Putaway Engine API delay
    setTimeout(() => {
      setIsEngineCalculating(false);
      // Backend Putaway Engine assigns best bin based on availability and SKU compatibility
      const targetBinId = receipt.suggestedBin || "A-01-01-01";
      
      setEngineRecommendation({
        binId: targetBinId,
        score: 98,
        reason: `Uygun Zone (Standart Depolama), Göz Doluluğu: %15, Yakınlık katsayısı yüksek. Ağırlık/Hacim limitleri doğrulanmıştır.`,
      });
    }, 1200);
  };

  const handleCompletePutaway = (receipt: InboundReceipt) => {
    if (!engineRecommendation) return;

    const targetBinId = engineRecommendation.binId;
    
    // Find target storage location in global shared data
    const location = MOCK_LOCATIONS_DATA.find((loc) => loc.code === targetBinId);
    
    if (location) {
      // 1) Verify weight & volume capacity
      const willExceedVolume = location.volumeOccupied + receipt.volume > location.maxVolume;
      const willExceedWeight = location.weightOccupied + receipt.weight > location.maxWeight;

      if (willExceedVolume || willExceedWeight) {
        alert(`PUTAWAY ENGELLENDİ: Göz (${targetBinId}) kapasitesi aşılıyor! Lütfen başka bir göz seçin.`);
        return;
      }

      if (location.status === "BLOCKED") {
        alert(`PUTAWAY ENGELLENDİ: Hedef göz (${targetBinId}) Bloke (BLOCKED) durumundadır.`);
        return;
      }

      // 2) Modify state in global array
      location.volumeOccupied = Number((location.volumeOccupied + receipt.volume).toFixed(2));
      location.weightOccupied += receipt.weight;

      // Add or merge SKU in that location
      const existingSku = location.skus.find(s => s.sku === receipt.sku);
      if (existingSku) {
        existingSku.qty += receipt.qty;
      } else {
        location.skus.push({
          sku: receipt.sku,
          name: receipt.skuName,
          qty: receipt.qty,
          lot: receipt.lot
        });
      }

      // Check if full
      if (location.volumeOccupied >= location.maxVolume || location.weightOccupied >= location.maxWeight) {
        location.status = "FULL";
      }

      // 3) Mark Receipt Completed
      setReceipts(prev =>
        prev.map(r => (r.id === receipt.id ? { ...r, status: "COMPLETED" } : r))
      );

      alert(`Mal Kabul Başarılı! ${receipt.qty} Adet ${receipt.sku} ürünü ${targetBinId} adresine yerleştirildi.`);
      setSelectedReceipt(null);
      setEngineRecommendation(null);
    } else {
      alert("Hata: Hedef depo adresi bulunamadı.");
    }
  };

  return (
    <div style={styles.container}>
      <div className="layout-split">
        {/* Left Side: Receipt List */}
        <div className="glass-card" style={styles.mainCard}>
          <div style={styles.cardHeader}>
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <Inbox size={22} color="var(--neon-purple)" />
              <h3 style={{ fontSize: "1.1rem", fontWeight: 600 }}>{t("ops.inbound.title")}</h3>
            </div>
            <span className="badge badge-purple">{receipts.length} Fiş</span>
          </div>

          <div style={styles.receiptList}>
            {receipts.map(rec => {
              const isActive = selectedReceipt?.id === rec.id;
              return (
                <div
                  key={rec.id}
                  onClick={() => {
                    setSelectedReceipt(rec);
                    setEngineRecommendation(null);
                  }}
                  style={{
                    ...styles.receiptCard,
                    borderColor: isActive ? "var(--neon-purple)" : "rgba(255, 255, 255, 0.08)",
                    boxShadow: isActive ? "var(--shadow-neon-purple)" : "none",
                    background: isActive ? "rgba(217, 70, 239, 0.05)" : "rgba(14, 19, 34, 0.4)",
                  }}
                >
                  <div style={styles.receiptCardHeader}>
                    <span style={styles.recId}>{rec.id}</span>
                    <span className={`badge ${
                      rec.status === "PENDING_QC"
                        ? "badge-orange"
                        : rec.status === "QC_APPROVED"
                          ? "badge-blue"
                          : "badge-green"
                    }`}>
                      {rec.status === "PENDING_QC" ? "QC Bekliyor" : rec.status === "QC_APPROVED" ? "QC Onaylı" : "Tamamlandı"}
                    </span>
                  </div>
                  <div style={styles.recSupplier}>{rec.supplier}</div>
                  <div style={styles.recSkuLine}>
                    <span>SKU: <strong>{rec.sku}</strong></span>
                    <span>Adet: <strong>{rec.qty}</strong></span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Side: Quality Control & Putaway Action */}
        <div className="glass-card" style={styles.actionCard}>
          {selectedReceipt ? (
            <div style={styles.actionBody}>
              <h3 style={styles.actionTitle}>Mal Kabul İşlemleri: {selectedReceipt.id}</h3>
              
              <div style={styles.detailsBox}>
                <div style={styles.detailRow}><span>Ürün:</span> <strong>{selectedReceipt.skuName} ({selectedReceipt.sku})</strong></div>
                <div style={styles.detailRow}><span>Miktar:</span> <strong>{selectedReceipt.qty} Adet</strong></div>
                <div style={styles.detailRow}><span>Lot No:</span> <strong>{selectedReceipt.lot}</strong></div>
                <div style={styles.detailRow}><span>Hacim/Ağırlık:</span> <span>{selectedReceipt.volume} m³ / {selectedReceipt.weight} kg</span></div>
              </div>

              {/* QC Checklist Progress */}
              <div style={{ marginTop: "20px" }}>
                <h4 style={styles.subHeader}>1. Kalite Kontrol (Quality Control)</h4>
                {selectedReceipt.status === "PENDING_QC" ? (
                  <button
                    className="btn btn-primary"
                    style={{ width: "100%", marginTop: "10px" }}
                    onClick={() => handleOpenQC(selectedReceipt)}
                  >
                    <ClipboardCheck size={18} /> QC Kontrol Formunu Aç
                  </button>
                ) : (
                  <div style={styles.qcSuccessBox}>
                    <CheckCircle2 size={18} color="var(--neon-green)" />
                    <span style={{ color: "var(--neon-green)", fontWeight: 600 }}>QC OK: Kalite onaylandı.</span>
                  </div>
                )}
              </div>

              {/* Putaway Recommendation Engine */}
              {selectedReceipt.status !== "PENDING_QC" && (
                <div style={{ marginTop: "25px" }}>
                  <h4 style={styles.subHeader}>2. Yönlendirmeli Raf Yerleştirme (Directed Putaway)</h4>
                  
                  {selectedReceipt.status === "COMPLETED" ? (
                    <div style={styles.completeBox}>
                      <CheckCircle2 size={32} color="var(--neon-green)" />
                      <p>Bu fiş başarıyla rafa kaldırıldı.</p>
                    </div>
                  ) : (
                    <>
                      {!engineRecommendation ? (
                        <button
                          className="btn btn-success"
                          style={{ width: "100%", marginTop: "10px" }}
                          disabled={isEngineCalculating}
                          onClick={() => handleGetSuggestion(selectedReceipt)}
                        >
                          <PlayCircle size={18} />
                          {isEngineCalculating ? "Yerleştirme Önerisi Alınıyor..." : "Yerleştirme Önerisi Getir"}
                        </button>
                      ) : (
                        <div style={styles.recommendationCard}>
                          <div style={styles.recTargetRow}>
                            <MapPin size={22} color="var(--neon-green)" />
                            <div>
                              <span style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>Önerilen Göz Adresi</span>
                              <h4 style={styles.recTargetBin}>{engineRecommendation.binId}</h4>
                            </div>
                            <span className="badge badge-green" style={{ marginLeft: "auto" }}>
                              Skor: {engineRecommendation.score}%
                            </span>
                          </div>
                          
                          <p style={styles.recTargetReason}>{engineRecommendation.reason}</p>

                          {/* Interactive preview map grid */}
                          <div style={styles.miniMap}>
                            <span style={styles.miniMapTitle}>Mini Depo Grid Görünümü</span>
                            <div style={styles.miniGridFlex}>
                              {["01", "02", "03"].map(bay => (
                                <div
                                  key={bay}
                                  className={engineRecommendation.binId.includes(`-02-02-01`) && bay === "02" ? "pulse-target" : ""}
                                  style={{
                                    ...styles.miniGridCell,
                                    border: engineRecommendation.binId.includes(`-02-02-01`) && bay === "02" ? "1.5px solid var(--neon-green)" : "1px solid var(--glass-border)",
                                    background: engineRecommendation.binId.includes(`-02-02-01`) && bay === "02" ? "rgba(0, 245, 155, 0.15)" : "transparent"
                                  }}
                                >
                                  {engineRecommendation.binId.split("-")[0]}-{bay}-02
                                </div>
                              ))}
                            </div>
                          </div>

                          <div style={styles.actionBtnsRow}>
                            <button
                              className="btn btn-secondary"
                              style={{ flexGrow: 1 }}
                              onClick={() => onNavigateToMap(engineRecommendation.binId)}
                            >
                              <Map size={16} /> Haritada Göster
                            </button>
                            <button
                              className="btn btn-success"
                              style={{ flexGrow: 1 }}
                              onClick={() => handleCompletePutaway(selectedReceipt)}
                            >
                              Yerleşimi Tamamla
                            </button>
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div style={styles.emptyContainer}>
              <Package size={48} color="var(--text-muted)" style={{ marginBottom: "15px" }} />
              <p>{t("ops.inbound.selectReceipt")}</p>
            </div>
          )}
        </div>
      </div>

      {/* QC CHECKLIST MODAL POPUP */}
      {showQcModal && selectedReceipt && (
        <div className="modal-backdrop">
          <div className="glass-card modal-wrapper">
            <div style={styles.modalHeader}>
              <h3>Kalite Kontrol (QC Checklist)</h3>
              <button className="close-btn" onClick={() => setShowQcModal(false)}><X size={20} /></button>
            </div>
            <div style={styles.modalBody}>
              <p style={{ fontSize: "0.85rem", color: "var(--text-secondary)", marginBottom: "15px" }}>
                Aşağıdaki kalite standart checklist kutularını işaretleyerek malın hasarsız ve uygun olduğunu onaylayın.
              </p>

              <div style={styles.checklistList}>
                <label style={styles.checkLabel}>
                  <input
                    type="checkbox"
                    checked={qcChecklist.packagingOk}
                    onChange={(e) => setQcChecklist(prev => ({ ...prev, packagingOk: e.target.checked }))}
                    style={styles.checkboxInput}
                  />
                  <div>
                    <strong>Koli Dış Ambalaj Kontrolü</strong>
                    <p style={styles.checkDesc}>Ezilme, ıslanma veya yırtılma hasarı bulunmamaktadır.</p>
                  </div>
                </label>

                <label style={styles.checkLabel}>
                  <input
                    type="checkbox"
                    checked={qcChecklist.quantityOk}
                    onChange={(e) => setQcChecklist(prev => ({ ...prev, quantityOk: e.target.checked }))}
                    style={styles.checkboxInput}
                  />
                  <div>
                    <strong>Miktar ve SKU Eşleşme Kontrolü</strong>
                    <p style={styles.checkDesc}>Fiili gelen miktar ({selectedReceipt.qty}) ile fatura/PO miktarı birebir uyuşmaktadır.</p>
                  </div>
                </label>

                <label style={styles.checkLabel}>
                  <input
                    type="checkbox"
                    checked={qcChecklist.tempControlOk}
                    onChange={(e) => setQcChecklist(prev => ({ ...prev, tempControlOk: e.target.checked }))}
                    style={styles.checkboxInput}
                  />
                  <div>
                    <strong>Isı ve Nem Koşulları</strong>
                    <p style={styles.checkDesc}>Taşıyıcı tır ısı limitleri standartlara uygundur.</p>
                  </div>
                </label>

                <label style={styles.checkLabel}>
                  <input
                    type="checkbox"
                    checked={qcChecklist.labelingOk}
                    onChange={(e) => setQcChecklist(prev => ({ ...prev, labelingOk: e.target.checked }))}
                    style={styles.checkboxInput}
                  />
                  <div>
                    <strong>Lot ve Barkod Etiketleme Kontrolü</strong>
                    <p style={styles.checkDesc}>Lot no ({selectedReceipt.lot}) okunabilir durumdadır ve etiketlenmiştir.</p>
                  </div>
                </label>
              </div>

              <div style={{ display: "flex", gap: "10px", marginTop: "20px" }}>
                <button className="btn btn-secondary" style={{ flexGrow: 1 }} onClick={() => setShowQcModal(false)}>İptal</button>
                <button className="btn btn-success" style={{ flexGrow: 1 }} onClick={handleQcSubmit}>
                  QC Kaliteyi Onayla
                </button>
              </div>
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
  mainCard: {
    padding: "20px",
    display: "flex",
    flexDirection: "column",
    maxHeight: "75vh",
    overflowY: "auto",
  },
  cardHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "15px",
    marginBottom: "15px",
  },
  receiptList: {
    display: "flex",
    flexDirection: "column",
    gap: "12px",
  },
  receiptCard: {
    border: "1px solid var(--glass-border)",
    borderRadius: "10px",
    padding: "16px",
    cursor: "pointer",
    display: "flex",
    flexDirection: "column",
    gap: "8px",
    textAlign: "left",
    transition: "var(--transition-smooth)",
  },
  receiptCardHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
  },
  recId: {
    fontWeight: 700,
    fontSize: "0.95rem",
    color: "var(--text-primary)",
  },
  recSupplier: {
    fontSize: "0.8rem",
    color: "var(--text-secondary)",
  },
  recSkuLine: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.85rem",
    color: "var(--text-muted)",
  },
  actionCard: {
    padding: "25px",
    minHeight: "50vh",
    display: "flex",
    flexDirection: "column",
  },
  actionBody: {
    display: "flex",
    flexDirection: "column",
    textAlign: "left",
  },
  actionTitle: {
    fontSize: "1.2rem",
    fontWeight: 600,
    marginBottom: "15px",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "10px",
  },
  detailsBox: {
    background: "rgba(255,255,255,0.02)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "15px",
    display: "flex",
    flexDirection: "column",
    gap: "10px",
  },
  detailRow: {
    display: "flex",
    justifyContent: "space-between",
    fontSize: "0.85rem",
    color: "var(--text-secondary)",
  },
  subHeader: {
    fontSize: "0.95rem",
    fontWeight: 600,
    color: "var(--text-secondary)",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "6px",
    marginBottom: "12px",
  },
  qcSuccessBox: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    background: "rgba(0, 245, 155, 0.1)",
    border: "1px solid rgba(0, 245, 155, 0.3)",
    borderRadius: "8px",
    padding: "12px",
    marginTop: "10px",
  },
  recommendationCard: {
    background: "rgba(255,255,255,0.01)",
    border: "1px solid var(--glass-border)",
    borderRadius: "10px",
    padding: "16px",
    marginTop: "12px",
    display: "flex",
    flexDirection: "column",
    gap: "12px",
  },
  recTargetRow: {
    display: "flex",
    alignItems: "center",
    gap: "12px",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "10px",
  },
  recTargetBin: {
    fontSize: "1.4rem",
    fontWeight: 700,
    color: "var(--neon-green)",
    textShadow: "0 0 10px rgba(0, 245, 155, 0.2)",
  },
  recTargetReason: {
    fontSize: "0.8rem",
    color: "var(--text-secondary)",
    lineHeight: "1.4",
  },
  miniMap: {
    background: "rgba(0,0,0,0.2)",
    borderRadius: "8px",
    padding: "12px",
    border: "1px solid var(--glass-border)",
  },
  miniMapTitle: {
    display: "block",
    fontSize: "0.7rem",
    color: "var(--text-muted)",
    marginBottom: "8px",
    textTransform: "uppercase",
  },
  miniGridFlex: {
    display: "flex",
    gap: "8px",
    justifyContent: "space-between",
  },
  miniGridCell: {
    flexGrow: 1,
    padding: "8px 4px",
    borderRadius: "6px",
    fontSize: "0.7rem",
    textAlign: "center",
    color: "var(--text-secondary)",
  },
  actionBtnsRow: {
    display: "flex",
    gap: "10px",
    marginTop: "8px",
  },
  emptyContainer: {
    flexGrow: 1,
    display: "flex",
    flexDirection: "column",
    justifyContent: "center",
    alignItems: "center",
    color: "var(--text-muted)",
    fontSize: "0.9rem",
    textAlign: "center",
  },
  completeBox: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    gap: "10px",
    background: "rgba(0,245,155,0.06)",
    border: "1px dashed var(--neon-green)",
    borderRadius: "8px",
    padding: "30px 10px",
    marginTop: "10px",
    color: "var(--neon-green)",
    fontWeight: 600,
  },
  modalHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottom: "1px solid var(--glass-border)",
    paddingBottom: "12px",
    marginBottom: "15px",
  },
  modalBody: {
    textAlign: "left",
  },
  checklistList: {
    display: "flex",
    flexDirection: "column",
    gap: "14px",
  },
  checkLabel: {
    display: "flex",
    alignItems: "flex-start",
    gap: "12px",
    padding: "10px",
    borderRadius: "8px",
    background: "rgba(255, 255, 255, 0.01)",
    border: "1px solid var(--glass-border)",
    cursor: "pointer",
  },
  checkboxInput: {
    marginTop: "3px",
    width: "16px",
    height: "16px",
    accentColor: "var(--neon-green)",
  },
  checkDesc: {
    fontSize: "0.75rem",
    color: "var(--text-muted)",
    marginTop: "2px",
  },
};
