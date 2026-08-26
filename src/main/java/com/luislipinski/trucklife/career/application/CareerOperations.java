package com.luislipinski.trucklife.career.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import java.util.List;
import java.util.UUID;

public interface CareerOperations {

    CareerEntity create(UUID userId, CreateCareerCommand command);

    List<CareerEntity> list(UUID userId, CareerGame game);

    CareerEntity get(UUID userId, CareerGame game, UUID careerId);

    CareerEntity updateProfile(
            UUID userId,
            CareerGame game,
            UUID careerId,
            UpdateCareerProfileCommand command
    );
}
