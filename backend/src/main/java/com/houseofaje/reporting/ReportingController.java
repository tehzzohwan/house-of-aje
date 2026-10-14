package com.houseofaje.reporting;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportingController {

    private final ReportingService reporting;

    public ReportingController(ReportingService reporting) {
        this.reporting = reporting;
    }

    @GetMapping("/profit")
    public ProfitReport profit(@RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to) {
        return reporting.profit(from, to);
    }

    @GetMapping("/bookings-per-service")
    public List<ServiceBookings> bookingsPerService(@RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to) {
        return reporting.bookingsPerService(from, to);
    }

    @GetMapping("/best-selling")
    public List<BestSeller> bestSelling(@RequestParam(required = false) LocalDate from,
                                        @RequestParam(required = false) LocalDate to) {
        return reporting.bestSelling(from, to);
    }

    @GetMapping("/loyalty")
    public List<LoyalCustomer> loyalty(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(defaultValue = "10") int limit) {
        return reporting.loyalty(from, to, limit);
    }

    @GetMapping("/staff-performance")
    public List<StaffPerformance> staffPerformance(@RequestParam(required = false) LocalDate from,
                                                   @RequestParam(required = false) LocalDate to) {
        return reporting.staffPerformance(from, to);
    }

    @GetMapping(value = "/bookings.csv", produces = "text/csv")
    public ResponseEntity<String> bookingsCsv(@RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookings.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reporting.bookingsCsv(from, to));
    }

    @GetMapping(value = "/payments.csv", produces = "text/csv")
    public ResponseEntity<String> paymentsCsv(@RequestParam(required = false) LocalDate from,
                                              @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payments.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reporting.paymentsCsv(from, to));
    }
}
