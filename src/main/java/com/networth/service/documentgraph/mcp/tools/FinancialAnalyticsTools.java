package com.networth.service.documentgraph.mcp.tools;

import com.networth.model.entity.Goal;
import com.networth.model.entity.Liability;
import com.networth.service.EMIService;
import com.networth.service.GoalService;
import com.networth.service.HealthScoreService;
import com.networth.service.RebalancingService;
import com.networth.service.SIPCalendarService;
import com.networth.service.SalaryService;
import com.networth.service.SpendAnalyticsService;
import com.networth.service.analytics.AnalyticsService;
import com.networth.service.market.NewsService;
import com.networth.service.networth.NetWorthService;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.PortfolioSummaryService;
import com.networth.service.tax.CapitalGainsCalculator;
import com.networth.service.tax.TaxRegimeCalculator;
import com.networth.service.tax.rules.TaxRuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialAnalyticsTools {

    private final NetWorthService netWorthService;
    private final PortfolioSummaryService portfolioSummaryService;
    private final HealthScoreService healthScoreService;
    private final HoldingService holdingService;
    private final AnalyticsService analyticsService;
    private final CapitalGainsCalculator capitalGainsCalculator;
    private final TaxRegimeCalculator taxRegimeCalculator;
    private final TaxRuleRegistry taxRuleRegistry;
    private final GoalService goalService;
    private final EMIService emiService;
    private final SIPCalendarService sipCalendarService;
    private final SpendAnalyticsService spendAnalyticsService;
    private final NewsService newsService;
    private final RebalancingService rebalancingService;
    private final SalaryService salaryService;

    // ── Net Worth & Overview ─────────────────────────────────────

    @Tool(name = "get_net_worth", description = "Calculate user's total net worth with breakdown by asset class (equity, debt, gold, real estate, cash, crypto) and liabilities")
    public Map<String, Object> getNetWorth(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            var nw = netWorthService.calculateNetWorth(uid);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("netWorth", nw.getNetWorth());
            result.put("totalAssets", nw.getTotalAssets());
            result.put("totalLiabilities", nw.getTotalLiabilities());
            result.put("equityValue", nw.getEquityValue());
            result.put("debtValue", nw.getDebtValue());
            result.put("goldValue", nw.getGoldValue());
            result.put("realEstateValue", nw.getRealEstateValue());
            result.put("cashValue", nw.getCashValue());
            result.put("cryptoValue", nw.getCryptoValue());
            result.put("liquidAssets", nw.getLiquidAssets());
            return result;
        } catch (Exception e) {
            return errorResult("Failed to calculate net worth", e);
        }
    }

    @Tool(name = "get_portfolio_summary", description = "Get portfolio overview: total invested, current value, profit/loss, returns percentage, asset allocation, and number of holdings")
    public Map<String, Object> getPortfolioSummary(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            var summary = portfolioSummaryService.getSummary(uid);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalInvested", summary.getTotalInvested());
            result.put("currentValue", summary.getCurrentValue());
            result.put("totalPnl", summary.getTotalPnl());
            result.put("unrealizedPnl", summary.getUnrealizedPnl());
            result.put("realizedPnl", summary.getRealizedPnl());
            result.put("absoluteReturn", summary.getAbsoluteReturn());
            result.put("totalHoldings", summary.getTotalHoldings());
            result.put("assetAllocation", summary.getAssetAllocation());
            return result;
        } catch (Exception e) {
            return errorResult("Failed to get portfolio summary", e);
        }
    }

    @Tool(name = "get_financial_health_score", description = "Calculate financial health score (0-100) across 6 dimensions: emergency fund, debt health, savings rate, diversification, liquidity, and goals progress. Returns score, grade, and actionable recommendations.")
    public Map<String, Object> getFinancialHealthScore(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return healthScoreService.calculateHealthScore(uid);
        } catch (Exception e) {
            return errorResult("Failed to calculate health score", e);
        }
    }

    // ── Portfolio Analytics ──────────────────────────────────────

    @Tool(name = "get_asset_allocation", description = "Get portfolio asset allocation breakdown by type (equity, mutual funds, gold, crypto, fixed income, etc.) with percentages")
    public Map<String, Object> getAssetAllocation(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return analyticsService.getAssetAllocation(uid);
        } catch (Exception e) {
            return errorResult("Failed to get asset allocation", e);
        }
    }

    @Tool(name = "get_sector_allocation", description = "Get portfolio sector allocation breakdown (IT, Banking, FMCG, Pharma, etc.) with percentages")
    public Map<String, Object> getSectorAllocation(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return analyticsService.getSectorAllocation(uid);
        } catch (Exception e) {
            return errorResult("Failed to get sector allocation", e);
        }
    }

    @Tool(name = "calculate_xirr", description = "Calculate the true annualized return (XIRR) of the user's portfolio, accounting for all cash flows and timing")
    public Map<String, Object> calculateXirr(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            BigDecimal xirr = analyticsService.calculateXIRR(uid);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("xirr", xirr);
            result.put("xirrPercentage", xirr != null ? xirr.multiply(BigDecimal.valueOf(100)) + "%" : "N/A");
            return result;
        } catch (Exception e) {
            return errorResult("Failed to calculate XIRR", e);
        }
    }

    // ── Tax ──────────────────────────────────────────────────────

    @Tool(name = "calculate_capital_gains", description = "Calculate capital gains tax for a financial year. Shows LTCG/STCG split per asset class (equity, debt, gold, crypto) with tax amounts.")
    public Map<String, Object> calculateCapitalGains(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Financial year in format YYYY-YYYY, e.g. 2024-2025") String financialYear) {
        try {
            UUID uid = UUID.fromString(userId);
            return capitalGainsCalculator.calculateCapitalGains(uid, financialYear);
        } catch (Exception e) {
            return errorResult("Failed to calculate capital gains", e);
        }
    }

    @Tool(name = "compare_tax_regimes", description = "Compare old vs new tax regime and recommend which saves more tax. Provide gross salary and deductions to get side-by-side comparison.")
    public Map<String, Object> compareTaxRegimes(
            @ToolParam(description = "Annual gross salary in INR") Number grossSalary,
            @ToolParam(description = "Total deductions under old regime (80C, 80D, HRA, etc.) in INR") Number totalDeductions,
            @ToolParam(description = "HRA exemption amount in INR (0 if not applicable)", required = false) Number hraExemption,
            @ToolParam(description = "Financial year, e.g. 2025-2026 or 2025-26. Defaults to the current financial year.", required = false) String financialYear) {
        try {
            BigDecimal salary = BigDecimal.valueOf(grossSalary.doubleValue());
            BigDecimal deductions = BigDecimal.valueOf(totalDeductions.doubleValue());
            BigDecimal hra = hraExemption != null ? BigDecimal.valueOf(hraExemption.doubleValue()) : BigDecimal.ZERO;
            String fy = (financialYear != null && !financialYear.isBlank())
                    ? financialYear : taxRuleRegistry.currentFinancialYear();
            var comparison = taxRegimeCalculator.compareRegimes(salary, deductions, hra, null, null, fy);
            return comparison.toMap();
        } catch (Exception e) {
            return errorResult("Failed to compare tax regimes", e);
        }
    }

    // ── Goals ────────────────────────────────────────────────────

    @Tool(name = "get_goals", description = "Get all user's financial goals with name, target amount, target date, and type")
    public List<Map<String, Object>> getGoals(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return goalService.getUserGoals(uid).stream().map(g -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", g.getId().toString());
                m.put("name", g.getName());
                m.put("targetAmount", g.getTargetAmount());
                m.put("targetDate", g.getTargetDate() != null ? g.getTargetDate().toString() : null);
                m.put("goalType", g.getGoalType());
                m.put("currentAmount", g.getCurrentAmount());
                return m;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of(errorResult("Failed to get goals", e));
        }
    }

    @Tool(name = "get_goal_progress", description = "Get detailed progress for a specific goal: percentage complete, shortfall, monthly target needed, days remaining, and whether on track")
    public Map<String, Object> getGoalProgress(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Goal ID (UUID)") String goalId) {
        try {
            UUID uid = UUID.fromString(userId);
            UUID gid = UUID.fromString(goalId);
            var progress = goalService.getGoalProgress(uid, gid);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("goalName", progress.getGoalName());
            result.put("targetAmount", progress.getTargetAmount());
            result.put("currentAmount", progress.getCurrentAmount());
            result.put("progressPercentage", progress.getProgressPercentage());
            result.put("shortfall", progress.getShortfall());
            result.put("daysRemaining", progress.getDaysRemaining());
            result.put("monthlyTarget", progress.getMonthlyTarget());
            result.put("isOnTrack", progress.isOnTrack());
            return result;
        } catch (Exception e) {
            return errorResult("Failed to get goal progress", e);
        }
    }

    // ── Liabilities & EMI ───────────────────────────────────────

    @Tool(name = "get_liabilities", description = "Get all user's loans and liabilities with outstanding amounts, interest rates, and EMI details")
    public List<Map<String, Object>> getLiabilities(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return emiService.getUserLiabilities(uid).stream().map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", l.getId().toString());
                m.put("type", l.getLiabilityType());
                m.put("lender", l.getLender());
                m.put("originalAmount", l.getOriginalAmount());
                m.put("outstandingAmount", l.getOutstandingAmount());
                m.put("interestRate", l.getInterestRate());
                m.put("monthlyEmi", l.getMonthlyEmi());
                m.put("startDate", l.getStartDate() != null ? l.getStartDate().toString() : null);
                m.put("endDate", l.getEndDate() != null ? l.getEndDate().toString() : null);
                return m;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of(errorResult("Failed to get liabilities", e));
        }
    }

    @Tool(name = "calculate_emi", description = "Calculate EMI for a loan. No user data needed — pure calculator. Returns monthly EMI amount, total payable, and total interest.")
    public Map<String, Object> calculateEmi(
            @ToolParam(description = "Loan principal amount in INR") Number principal,
            @ToolParam(description = "Annual interest rate (e.g. 8.5 for 8.5%)") Number annualRate,
            @ToolParam(description = "Loan tenure in months") Number tenureMonths) {
        try {
            BigDecimal p = BigDecimal.valueOf(principal.doubleValue());
            BigDecimal r = BigDecimal.valueOf(annualRate.doubleValue());
            long months = tenureMonths.longValue();
            BigDecimal emi = emiService.calculateEMI(p, r, months);
            BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(months));
            BigDecimal totalInterest = totalPayable.subtract(p);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("monthlyEmi", emi);
            result.put("totalPayable", totalPayable);
            result.put("totalInterest", totalInterest);
            result.put("principal", p);
            result.put("annualRate", r);
            result.put("tenureMonths", months);
            return result;
        } catch (Exception e) {
            return errorResult("Failed to calculate EMI", e);
        }
    }

    // ── SIP ──────────────────────────────────────────────────────

    @Tool(name = "get_sip_calendar", description = "Get SIP (Systematic Investment Plan) calendar: upcoming SIPs, missed SIPs, total monthly SIP amount, and per-holding SIP details")
    public Map<String, Object> getSipCalendar(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return sipCalendarService.getSIPCalendar(uid);
        } catch (Exception e) {
            return errorResult("Failed to get SIP calendar", e);
        }
    }

    // ── Spend Analytics ─────────────────────────────────────────

    @Tool(name = "get_spend_reports", description = "Get all credit card spend reports for the user, newest first. Shows card issuer, total due, transactions per statement.")
    public List<Map<String, Object>> getSpendReports(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return spendAnalyticsService.getUserReports(uid).stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId().toString());
                m.put("cardIssuer", r.getCardIssuer());
                m.put("cardLastFour", r.getCardLastFour());
                m.put("statementMonth", r.getStatementMonth());
                m.put("totalAmountDue", r.getTotalAmountDue());
                m.put("newCharges", r.getNewCharges());
                m.put("paid", r.getPaid());
                m.put("transactionCount", r.getTransactions() != null ? r.getTransactions().size() : 0);
                return m;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of(errorResult("Failed to get spend reports", e));
        }
    }

    @Tool(name = "get_monthly_spend", description = "Get detailed spend analysis for a specific month: category breakdown, top merchants, month-over-month change, per-card split")
    public Map<String, Object> getMonthlySpend(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Month in YYYY-MM format, e.g. 2025-01") String month) {
        try {
            UUID uid = UUID.fromString(userId);
            return spendAnalyticsService.getMonthAnalysis(uid, month);
        } catch (Exception e) {
            return errorResult("Failed to get monthly spend", e);
        }
    }

    @Tool(name = "get_spend_trend", description = "Get monthly spend totals over time for trend analysis")
    public List<Map<String, Object>> getSpendTrend(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return spendAnalyticsService.getMonthlyTrend(uid);
        } catch (Exception e) {
            return List.of(errorResult("Failed to get spend trend", e));
        }
    }

    @Tool(name = "get_payment_summary", description = "Get CC payment summary: total billed, total paid, current outstanding, unpaid bills count, next due date")
    public Map<String, Object> getPaymentSummary(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            return spendAnalyticsService.getPaymentSummary(uid);
        } catch (Exception e) {
            return errorResult("Failed to get payment summary", e);
        }
    }

    // ── News & Market ───────────────────────────────────────────

    @Tool(name = "search_news", description = "Search financial news by keyword. Returns headlines, sources, and links.")
    public List<Map<String, Object>> searchNews(
            @ToolParam(description = "Search query (stock name, topic, etc.)") String query,
            @ToolParam(description = "Maximum results to return", required = false) Integer limit) {
        try {
            return newsService.searchNews(query, limit != null && limit > 0 ? limit : 5);
        } catch (Exception e) {
            return List.of(errorResult("Failed to search news", e));
        }
    }

    @Tool(name = "get_portfolio_news", description = "Get recent news for all stocks in the user's portfolio")
    public List<Map<String, Object>> getPortfolioNews(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            return newsService.getNewsForHoldings(userId, 3);
        } catch (Exception e) {
            return List.of(errorResult("Failed to get portfolio news", e));
        }
    }

    // ── Rebalancing ─────────────────────────────────────────────

    @Tool(name = "get_rebalancing_suggestions", description = "Get portfolio rebalancing suggestions based on risk profile (conservative/moderate/aggressive). Shows target vs current allocation with buy/sell recommendations.")
    public List<Map<String, Object>> getRebalancingSuggestions(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Risk profile: conservative, moderate, or aggressive") String riskProfile) {
        try {
            UUID uid = UUID.fromString(userId);
            return rebalancingService.getRebalancingSuggestions(uid, riskProfile != null ? riskProfile : "moderate");
        } catch (Exception e) {
            return List.of(errorResult("Failed to get rebalancing suggestions", e));
        }
    }

    @Tool(name = "get_loan_summary", description = "Get detailed summary of a specific loan: outstanding amount, total interest, progress percentage, remaining months")
    public Map<String, Object> getLoanSummary(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Liability/Loan ID (UUID)") String liabilityId) {
        try {
            UUID uid = UUID.fromString(userId);
            UUID lid = UUID.fromString(liabilityId);
            return emiService.getLoanSummary(uid, lid);
        } catch (Exception e) {
            return errorResult("Failed to get loan summary", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private Map<String, Object> errorResult(String message, Exception e) {
        String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        log.warn("{}: {}", message, errMsg);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", message + ": " + errMsg);
        return err;
    }

    // ── Salary Tools ─────────────────────────────────────

    @Tool(name = "list_salaries", description = "List all salary/payslip records for the user, with employer, amount, date, and components")
    public Map<String, Object> listSalaries(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            UUID uid = UUID.fromString(userId);
            var salaries = salaryService.getUserSalaries(uid);
            List<Map<String, Object>> items = salaries.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", s.getId().toString());
                m.put("employerName", s.getEmployerName());
                m.put("amount", s.getAmount());
                m.put("payDate", s.getPayDate() != null ? s.getPayDate().toString() : null);
                m.put("notes", s.getNotes());
                m.put("components", s.getComponents());
                return m;
            }).collect(Collectors.toList());
            return Map.of("salaries", items, "count", items.size());
        } catch (Exception e) {
            return errorResult("Failed to list salaries", e);
        }
    }
}
