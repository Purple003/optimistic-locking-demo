package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;

import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javax.persistence.EntityManager;

public class ConcurrentReservationSimulator {
    
    private static final EntityManagerFactory emf = 
        Persistence.createEntityManagerFactory("optimistic-locking-demo");
    private static final ReservationService reservationService = new ReservationServiceImpl(emf);
    
    public static void main(String[] args) throws InterruptedException {
        // Initialisation des données
        initData();
        
        // Simulation d'un conflit de réservation concurrent
        simulateConcurrentReservationConflict();
        
        // Fermeture de l'EntityManagerFactory
        emf.close();
    }
    
    private static void initData() {
        // Création d'un utilisateur
        Utilisateur utilisateur1 = new Utilisateur("Dupont", "Jean", "jean.dupont@example.com");
        Utilisateur utilisateur2 = new Utilisateur("Martin", "Sophie", "sophie.martin@example.com");
        
        // Création d'une salle
        Salle salle = new Salle("Salle A101", 30);
        salle.setDescription("Salle de réunion équipée d'un projecteur");
        
        // Persistance des entités
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("optimistic-locking-demo");
        try {
            
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            
            em.persist(utilisateur1);
            em.persist(utilisateur2);
            em.persist(salle);
            
            // Création d'une réservation
            Reservation reservation = new Reservation(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                "Réunion d'équipe"
            );
            reservation.setUtilisateur(utilisateur1);
            reservation.setSalle(salle);
            
            em.persist(reservation);
            
            em.getTransaction().commit();
            em.close();
            
            System.out.println("Données initialisées avec succès !");
            System.out.println("Réservation créée : " + reservation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void simulateConcurrentReservationConflict() throws InterruptedException {
        // Récupération de la réservation
        Optional<Reservation> reservationOpt = reservationService.findById(1L);
        if (!reservationOpt.isPresent()) {
            System.out.println("Réservation non trouvée !");
            return;
        }
        
        Reservation reservation = reservationOpt.get();
        System.out.println("Réservation récupérée : " + reservation);
        
        // Création de deux threads qui vont modifier la même réservation
        CountDownLatch latch = new CountDownLatch(1);
        
        Thread thread1 = new Thread(() -> {
            try {
                // Attendre que les deux threads soient prêts
                latch.await();
                
                // Premier thread : modification du motif
                Reservation r1 = reservationService.findById(1L).get();
                System.out.println("Thread 1 : Réservation récupérée, version = " + r1.getVersion());
                
                // Simuler un traitement long
                Thread.sleep(1000);
                
                r1.setMotif("Réunion d'équipe modifiée par Thread 1");
                try {
                    reservationService.update(r1);
                    System.out.println("Thread 1 : Réservation mise à jour avec succès !");
                } catch (OptimisticLockException e) {
                    System.out.println("Thread 1 : Conflit de verrouillage optimiste détecté !");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        Thread thread2 = new Thread(() -> {
            try {
                // Attendre que les deux threads soient prêts
                latch.await();
                
                // Deuxième thread : modification des dates
                Reservation r2 = reservationService.findById(1L).get();
                System.out.println("Thread 2 : Réservation récupérée, version = " + r2.getVersion());
                
                // Modification immédiate
                r2.setDateDebut(r2.getDateDebut().plusHours(1));
                r2.setDateFin(r2.getDateFin().plusHours(1));
                
                try {
                    reservationService.update(r2);
                    System.out.println("Thread 2 : Réservation mise à jour avec succès !");
                } catch (OptimisticLockException e) {
                    System.out.println("Thread 2 : Conflit de verrouillage optimiste détecté !");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        // Démarrage des threads
        thread1.start();
        thread2.start();
        
        // Libération du latch pour que les deux threads commencent en même temps
        latch.countDown();
        
        // Attendre que les deux threads terminent
        thread1.join();
        thread2.join();
        
        // Vérification de l'état final de la réservation
        Optional<Reservation> finalReservationOpt = reservationService.findById(1L);
        finalReservationOpt.ifPresent(r -> {
            System.out.println("\nÉtat final de la réservation :");
            System.out.println("ID : " + r.getId());
            System.out.println("Motif : " + r.getMotif());
            System.out.println("Date début : " + r.getDateDebut());
            System.out.println("Date fin : " + r.getDateFin());
            System.out.println("Version : " + r.getVersion());
        });
    }
}