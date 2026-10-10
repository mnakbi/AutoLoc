package tn.esprit.autoloc.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Démontre cascade ALL et orphanRemoval sur Contrat -> Paiement.
 * Chaque étape s'exécute dans sa propre transaction (commit réel en base).
 * Attention : ce test écrit dans autoloc_db et nettoie ses données à la fin.
 */
@SpringBootTest
class ContratPaiementCascadeTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private long countPaiements(Long idContrat) {
        return em.createQuery(
                        "select count(p) from Paiement p where p.contrat.idContrat = :id", Long.class)
                .setParameter("id", idContrat)
                .getSingleResult();
    }

    private long compter(TransactionTemplate tx, Long idContrat) {
        Long total = tx.execute(status -> countPaiements(idContrat));
        return total == null ? 0L : total;
    }

    private Paiement paiement(String montant, ModePaiement mode) {
        Paiement p = new Paiement();
        p.setMontant(new BigDecimal(montant));
        p.setDatePaiement(LocalDate.now());
        p.setModePaiement(mode);
        return p;
    }

    @Test
    void cascadeEtOrphanRemoval() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // 1) PERSIST en cascade : on ne sauvegarde QUE le contrat
        Long idContrat = tx.execute(status -> {
            Contrat c = new Contrat();
            c.setDateSignature(LocalDate.now());
            c.setMontantTotal(new BigDecimal("500.00"));
            c.setValide(true);
            c.addPaiement(paiement("200.00", ModePaiement.CARTE));
            c.addPaiement(paiement("300.00", ModePaiement.ESPECES));
            em.persist(c);
            return c.getIdContrat();
        });
        assertNotNull(idContrat);
        assertEquals(2, compter(tx, idContrat),
                "Les 2 paiements doivent être enregistrés avec le contrat (cascade PERSIST)");

        // 2) orphanRemoval : on retire un paiement de la liste, le contrat reste
        tx.executeWithoutResult(status -> {
            Contrat c = em.find(Contrat.class, idContrat);
            c.removePaiement(c.getPaiements().get(0));
        });
        assertEquals(1, compter(tx, idContrat),
                "Le paiement retiré de la liste doit être supprimé en base (orphanRemoval)");
        assertNotNull(tx.execute(s -> em.find(Contrat.class, idContrat)),
                "Le contrat doit toujours exister");

        // 3) REMOVE en cascade : supprimer le contrat supprime ses paiements
        tx.executeWithoutResult(status ->
                em.remove(em.find(Contrat.class, idContrat)));
        assertEquals(0, compter(tx, idContrat),
                "Supprimer le contrat doit supprimer ses paiements (cascade REMOVE)");
    }
}