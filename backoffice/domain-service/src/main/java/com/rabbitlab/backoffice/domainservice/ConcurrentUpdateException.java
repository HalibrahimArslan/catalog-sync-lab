package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.DomainException;

/**
 * Kaydedilmek istenen kopya eskimiş: okunduktan sonra başkası değiştirmiş (optimistic locking).
 * Çağıran taraf güncel hâli okuyup işlemi tekrar denemeli. Repository sözleşmesinin parçası; bu yüzden
 * repository arayüzleriyle aynı halkada.
 */
public class ConcurrentUpdateException extends DomainException {

    public ConcurrentUpdateException(String aggregate, String id, long expectedVersion) {
        super("%s %s başkası tarafından değiştirildi (beklenen versiyon: %d)".formatted(aggregate, id, expectedVersion));
    }
}
