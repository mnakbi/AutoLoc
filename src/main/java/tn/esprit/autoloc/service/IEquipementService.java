package tn.esprit.autoloc.service;

import java.util.List;
import tn.esprit.autoloc.domain.Equipement;

public interface IEquipementService {

    Equipement create(Equipement equipement);

    Equipement findById(Long id);

    List<Equipement> findAll();

    Equipement update(Long id, Equipement equipement);

    void deleteById(Long id);
}
