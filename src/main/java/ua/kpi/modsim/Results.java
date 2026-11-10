package ua.kpi.modsim;

/**
 * Вихідні змінні одного прогону за період спостереження (після перехідного періоду).
 *
 * @param tObs          тривалість спостереження, хв
 * @param nIn           кількість деталей, що надійшли
 * @param nWaste        кількість відходів N_w
 * @param nOk           кількість придатних після первинної обробки N_ok
 * @param uP1           завантаження P1
 * @param uP2           завантаження P2
 * @param uS1           завантаження S1, формула (2.11)
 * @param uS2           завантаження S2, формула (2.10)
 * @param pWaste        імовірність відходів, формула (2.12)
 * @param meanQ0        середня довжина черги Q0
 * @param meanQR        середня довжина черги QR
 * @param meanQS        середня довжина накопичувача QS
 * @param meanLs        середня кількість деталей, що чекають вторинної обробки, формула (2.13)
 * @param shareFirstP1  частка деталей, що вперше пішли на P1 (q у формулі 2.15)
 * @param balanceOk     чи виконується баланс: надійшло = відходи + оброблено + у системі
 */
public record Results(double tObs, long nIn, long nWaste, long nOk,
                      double uP1, double uP2, double uS1, double uS2,
                      double pWaste, double meanQ0, double meanQR, double meanQS, double meanLs,
                      double shareFirstP1, boolean balanceOk) {

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "T=%.0f хв: надійшло=%d, відходи=%d, придатні=%d | U(P1)=%.4f U(P2)=%.4f U(S1)=%.4f U(S2)=%.4f"
                        + " | Pw=%.5f | сер.Q0=%.2f QR=%.3f QS=%.2f Ls=%.2f | q=%.3f | баланс=%s",
                tObs, nIn, nWaste, nOk, uP1, uP2, uS1, uS2, pWaste, meanQ0, meanQR, meanQS, meanLs,
                shareFirstP1, balanceOk ? "ОК" : "ПОМИЛКА");
    }
}
