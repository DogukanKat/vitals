package com.example.billing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService extends AbstractBillingService {

    private final InvoiceRepository invoiceRepository;

    public BillingService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public Invoice issue(Order order) {
        return invoiceRepository.save(Invoice.from(order));
    }

    @Override
    @Transactional
    protected void onIssued(Invoice invoice) {
        invoiceRepository.markIssued(invoice.getId());
    }
}
