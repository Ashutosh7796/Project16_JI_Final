package com.spring.jwt.service;

import com.spring.jwt.dto.AdminDashboardStatsDTO;
import com.spring.jwt.dto.OrderWeekPointDTO;
import com.spring.jwt.EmployeeFarmerSurvey.EmployeeFarmerSurveyRepository;
import com.spring.jwt.Product.ProductRepository;
import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedRepository;
import com.spring.jwt.ProductBuyPending.ProductBuyPendingRepository;
import com.spring.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final EmployeeFarmerSurveyRepository employeeFarmerSurveyRepository;
    private final ProductRepository productRepository;
    private final ProductBuyPendingRepository productBuyPendingRepository;
    private final ProductBuyConfirmedRepository productBuyConfirmedRepository;

    @Autowired
    @Qualifier("dashboardStatsExecutor")
    private Executor dashboardStatsExecutor;

    /**
     * Runs independent counts and weekly buckets in parallel (bounded executor, not ForkJoinPool.commonPool).
     * Each repository call runs in its own short transaction (no single enclosing read transaction).
     */
    public AdminDashboardStatsDTO buildStats() {
        final LocalDate today = LocalDate.now();
        final LocalDateTime newestEndExclusive = today.plusDays(1).atStartOfDay();
        Executor ex = dashboardStatsExecutor;

        CompletableFuture<Long> farmers =
                CompletableFuture.supplyAsync(() -> employeeFarmerSurveyRepository.count(), ex);
        CompletableFuture<Long> employees =
                CompletableFuture.supplyAsync(() -> userRepository.countActiveStaffUsers(), ex);
        CompletableFuture<Long> products =
                CompletableFuture.supplyAsync(() -> productRepository.count(), ex);
        CompletableFuture<Long> pending =
                CompletableFuture.supplyAsync(() -> productBuyPendingRepository.count(), ex);
        CompletableFuture<Long> confirmed =
                CompletableFuture.supplyAsync(() -> productBuyConfirmedRepository.count(), ex);

        List<CompletableFuture<OrderWeekPointDTO>> weekFutures = new ArrayList<>(8);
        for (int w = 0; w < 8; w++) {
            final int wi = w;
            weekFutures.add(
                    CompletableFuture.supplyAsync(
                            () -> {
                                LocalDateTime endExclusive =
                                        newestEndExclusive.minusDays(7L * (7 - wi));
                                LocalDateTime startInclusive = endExclusive.minusDays(7);
                                long actual =
                                        productBuyPendingRepository.countAllOrdersBetween(
                                                startInclusive, endExclusive);
                                long plan = computePlanTarget(actual);
                                return OrderWeekPointDTO.builder()
                                        .label("Wk " + (wi + 1))
                                        .plan(plan)
                                        .actual(actual)
                                        .build();
                            }, ex));
        }

        CompletableFuture<Void> weeksDone =
                CompletableFuture.allOf(weekFutures.toArray(new CompletableFuture[0]));
        CompletableFuture.allOf(farmers, employees, products, pending, confirmed, weeksDone)
                .join();

        List<OrderWeekPointDTO> orderTrack = weekFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        long farmersCount = farmers.join();
        long orders = pending.join() + confirmed.join();

        return AdminDashboardStatsDTO.builder()
                .totalUsers(farmersCount)
                .farmers(farmersCount)
                .employees(employees.join())
                .products(products.join())
                .orders(orders)
                .orderTrack(orderTrack)
                .build();
    }

    private static long computePlanTarget(long actual) {
        if (actual <= 0) {
            return 0L;
        }
        long uplifted = Math.round(actual * 1.18) + 2L;
        return Math.max(actual + 1, uplifted);
    }
}
