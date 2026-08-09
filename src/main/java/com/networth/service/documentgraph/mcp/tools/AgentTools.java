package com.networth.service.documentgraph.mcp.tools;

import com.networth.service.documentgraph.mcp.AgentContext;
import com.networth.service.documentgraph.mcp.AgentExecutor;
import com.networth.service.documentgraph.mcp.McpToolClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Expert agent tools. Each agent is a sub-orchestrator that runs its own
 * AI tool-calling loop with a scoped set of tools.
 *
 * The AI inside the agent decides which tools to call and in what order —
 * no hardcoded Java flow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentTools {

    private final AgentExecutor agentExecutor;

    // ═══════════════════════════════════════════════════════════
    //  STOCK ANALYST AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String STOCK_ANALYST_PROMPT = """
You are a Stock Analyst AI agent. You have access to tools to gather data about a specific stock.

Your job:
1. Search for the stock holding to get current position data
2. Get the portfolio summary for context
3. Look up recent news about the stock
4. Get asset and sector allocation to assess concentration risk
5. Compute XIRR if relevant

After gathering data, produce a comprehensive analysis:
- **Overview**: Stock name, sector, current position (qty, avg price, current price, PnL)
- **Performance**: Return %, price trends, comparison context
- **News & Sentiment**: Recent headlines and what they mean
- **Risk**: Concentration in portfolio, sector exposure
- **Recommendation**: Buy more / Hold / Trim / Sell with reasoning

Use specific INR numbers. Be direct and actionable. Call tools to get real data — do NOT make up numbers.
""";

    private static final Set<String> STOCK_ANALYST_TOOLS = Set.of(
            "search_holdings", "search_accounts", "search_credit_cards",
            "get_portfolio_summary", "get_net_worth",
            "get_asset_allocation", "get_sector_allocation",
            "calculate_xirr", "get_financial_health_score"
    );

    @Tool(name = "analyze_stock",
          description = "Deep analysis of a specific stock: the agent autonomously searches holdings, fetches news, analyzes risk, and produces a buy/hold/sell recommendation. Use when the user asks to analyze a specific stock in detail.")
    public Map<String, Object> analyzeStock(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Stock symbol or name (e.g. INFY, Reliance, TCS)") String symbolOrName) {
        try {
            String task = "Analyze the stock '" + symbolOrName + "' in the user's portfolio in detail. "
                    + "Search for it first, then gather all relevant data before producing your analysis.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(STOCK_ANALYST_PROMPT, task, STOCK_ANALYST_TOOLS, 4, userId, toolClient);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("analysis", result);
            return response;
        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Stock analyst agent failed: {}", err);
            return Map.of("error", "Analysis failed: " + err);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PORTFOLIO DOCTOR AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String PORTFOLIO_DOCTOR_PROMPT = """
You are a Portfolio Doctor AI agent. You have tools to gather comprehensive portfolio data.

Your job:
1. Get net worth for the full financial picture
2. Get portfolio summary (invested, current value, PnL)
3. Get asset allocation and sector allocation
4. Get financial health score
5. Search all holdings for the full list
6. Calculate XIRR for true returns

After gathering data, produce a health report:
- **Portfolio Snapshot**: Net worth, total value, overall returns
- **Asset Allocation**: Current vs recommended, over/under-weight areas
- **Sector Concentration**: Risks from over-exposure
- **Health Score**: Breakdown across dimensions
- **Top Concerns**: 3-5 specific issues ranked by severity
- **Action Plan**: 5-7 specific steps in priority order

Use specific INR numbers. Be actionable, not generic. Call tools to get real data.
""";

    private static final Set<String> PORTFOLIO_DOCTOR_TOOLS = Set.of(
            "search_holdings", "search_accounts", "search_credit_cards",
            "get_net_worth", "get_portfolio_summary",
            "get_asset_allocation", "get_sector_allocation",
            "get_financial_health_score", "calculate_xirr",
            "get_goals", "get_liabilities"
    );

    @Tool(name = "portfolio_doctor",
          description = "Comprehensive portfolio health check: the agent autonomously gathers net worth, allocations, risk metrics, and health score, then produces a detailed action plan. Use when the user wants a full portfolio review.")
    public Map<String, Object> portfolioDoctor(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Perform a comprehensive portfolio health check. "
                    + "Gather all relevant data (net worth, holdings, allocations, health score, goals, liabilities) "
                    + "then produce a detailed health report with an actionable improvement plan.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(PORTFOLIO_DOCTOR_PROMPT, task, PORTFOLIO_DOCTOR_TOOLS, 4, userId, toolClient);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("analysis", result);
            return response;
        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Portfolio doctor agent failed: {}", err);
            return Map.of("error", "Portfolio review failed: " + err);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TAX ADVISOR AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String TAX_ADVISOR_PROMPT = """
You are a Tax Advisor AI agent specializing in Indian income tax. You have tools to gather tax-related data.

Your job:
1. Calculate capital gains for the current financial year
2. Search holdings to identify unrealized gains/losses for tax harvesting
3. Get net worth and portfolio summary for context
4. Get goals and liabilities for complete picture

After gathering data, produce tax planning advice:
- **Capital Gains Summary**: LTCG/STCG by asset class, total tax liability
- **Tax Harvesting**: Holdings with unrealized losses that can offset gains
- **Savings Opportunities**: Specific actions to reduce tax burden
- **Regime Comparison**: If user provides salary info, compare old vs new
- **Timeline**: What to do before March 31 vs next FY

Use specific INR numbers. Reference actual holdings by name. Indian tax rules FY 2024-25.
Call tools to get real data — do NOT assume portfolio contents.
""";

    private static final Set<String> TAX_ADVISOR_TOOLS = Set.of(
            "search_holdings", "get_net_worth", "get_portfolio_summary",
            "calculate_capital_gains", "compare_tax_regimes",
            "get_goals", "get_liabilities", "get_financial_health_score"
    );

    @Tool(name = "tax_advisor",
          description = "Comprehensive tax planning: the agent autonomously computes capital gains, identifies tax harvesting opportunities, and produces actionable tax advice. Use when the user wants detailed tax help.")
    public Map<String, Object> taxAdvisor(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Provide comprehensive tax planning advice for the current financial year. "
                    + "Calculate capital gains, identify tax harvesting opportunities from holdings, "
                    + "and produce specific recommendations to minimize tax liability.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(TAX_ADVISOR_PROMPT, task, TAX_ADVISOR_TOOLS, 4, userId, toolClient);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("analysis", result);
            return response;
        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Tax advisor agent failed: {}", err);
            return Map.of("error", "Tax analysis failed: " + err);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GOAL PLANNER AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String GOAL_PLANNER_PROMPT = """
You are a Goal Planner AI agent. You help users plan and track financial goals.

Your job:
1. Get all user goals to understand their targets
2. Check progress on each goal
3. Get portfolio and net worth for context
4. Get SIP calendar to see existing systematic investments
5. Search holdings to understand available assets

After gathering data, produce a goal planning report:
- **Goals Overview**: Each goal with target, current progress, shortfall
- **On-Track Assessment**: Which goals are on track, which are behind
- **Monthly Investment Needed**: SIP amount required per goal
- **Asset Recommendations**: What to invest in for each goal based on timeline
- **Action Plan**: Specific steps to get back on track

Use INR. Be specific about timelines and amounts.
""";

    private static final Set<String> GOAL_PLANNER_TOOLS = Set.of(
            "get_goals", "get_goal_progress", "search_holdings",
            "get_net_worth", "get_portfolio_summary", "get_sip_calendar",
            "get_asset_allocation", "get_financial_health_score"
    );

    @Tool(name = "goal_planner",
          description = "Financial goal planning: autonomously checks all goals, assesses progress, calculates required SIPs, and produces an investment plan. Use when the user asks about goals, retirement planning, or saving for something.")
    public Map<String, Object> goalPlanner(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Create a comprehensive financial goal plan. Check all goals, assess progress, "
                    + "calculate required monthly investments, and recommend specific actions to achieve each goal.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(GOAL_PLANNER_PROMPT, task, GOAL_PLANNER_TOOLS, 4, userId, toolClient);

            return Map.of("analysis", result);
        } catch (Exception e) {
            return Map.of("error", "Goal planning failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DEBT OPTIMIZER AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String DEBT_OPTIMIZER_PROMPT = """
You are a Debt Optimizer AI agent. You help users manage and reduce their debt efficiently.

Your job:
1. Get all liabilities to see current loans
2. Get loan summaries for each liability
3. Get net worth and portfolio for context
4. Calculate EMI scenarios for prepayment options

After gathering data, produce a debt optimization report:
- **Debt Overview**: All loans with outstanding amounts, rates, EMIs
- **Total Debt Burden**: Total outstanding, total monthly EMI, debt-to-asset ratio
- **Priority Ranking**: Which loans to pay off first (highest rate first)
- **Prepayment Analysis**: Impact of extra payments on each loan
- **Refinancing Opportunities**: Loans where a lower rate could save money
- **Debt-Free Timeline**: How long until debt-free with current vs optimized payments

Use INR. Be specific about monthly savings and interest saved.
""";

    private static final Set<String> DEBT_OPTIMIZER_TOOLS = Set.of(
            "get_liabilities", "get_loan_summary", "calculate_emi",
            "get_net_worth", "get_portfolio_summary", "get_financial_health_score",
            "search_accounts"
    );

    @Tool(name = "debt_optimizer",
          description = "Debt optimization: autonomously analyzes all loans, calculates prepayment impact, ranks payoff priority, and produces a debt-free timeline. Use when the user asks about loans, debt, EMI optimization, or becoming debt-free.")
    public Map<String, Object> debtOptimizer(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Analyze all loans and debts. Rank them by priority, calculate prepayment scenarios, "
                    + "and produce a detailed debt reduction plan with timeline to become debt-free.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(DEBT_OPTIMIZER_PROMPT, task, DEBT_OPTIMIZER_TOOLS, 4, userId, toolClient);

            return Map.of("analysis", result);
        } catch (Exception e) {
            return Map.of("error", "Debt optimization failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SPEND ANALYZER AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String SPEND_ANALYZER_PROMPT = """
You are a Spend Analyzer AI agent. You analyze credit card spending patterns and suggest savings.

Your job:
1. Get all spend reports to see billing history
2. Get monthly spend analysis for recent months
3. Get spend trends over time
4. Get payment summary for outstanding bills
5. Search credit cards for card details

After gathering data, produce a spending analysis:
- **Monthly Overview**: Total spend, category breakdown, top merchants
- **Trends**: Is spending increasing or decreasing? Which categories are growing?
- **Budget Alerts**: Categories where spending seems excessive
- **Savings Opportunities**: Specific areas to cut back with estimated savings
- **Payment Status**: Any unpaid bills, upcoming due dates
- **Recommendations**: 5 actionable steps to reduce spending

Use INR. Compare month-over-month where possible.
""";

    private static final Set<String> SPEND_ANALYZER_TOOLS = Set.of(
            "get_spend_reports", "get_monthly_spend", "get_spend_trend",
            "get_payment_summary", "search_credit_cards",
            "get_net_worth"
    );

    @Tool(name = "spend_analyzer",
          description = "Credit card spend analysis: autonomously reviews all CC bills, identifies trends, flags excessive categories, and suggests savings. Use when the user asks about spending, CC bills, budgeting, or where their money goes.")
    public Map<String, Object> spendAnalyzer(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Analyze credit card spending patterns. Review recent bills, identify trends, "
                    + "flag excessive categories, and produce specific savings recommendations.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(SPEND_ANALYZER_PROMPT, task, SPEND_ANALYZER_TOOLS, 4, userId, toolClient);

            return Map.of("analysis", result);
        } catch (Exception e) {
            return Map.of("error", "Spend analysis failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MARKET SCOUT AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String MARKET_SCOUT_PROMPT = """
You are a Market Scout AI agent. You scan news and market data relevant to the user's portfolio.

Your job:
1. Search holdings to know what the user owns
2. Get portfolio news for all held stocks
3. Search news for specific topics if relevant
4. Get portfolio summary for context

After gathering data, produce a market briefing:
- **Portfolio News**: Key headlines for each holding, what they mean
- **Market Sentiment**: Overall sentiment for the user's holdings
- **Alerts**: Any urgent news (earnings, regulatory, sector shifts)
- **Opportunities**: Positive developments that could benefit holdings
- **Risks**: Negative news or headwinds to watch
- **Watchlist**: Stocks or sectors to monitor

Be specific about which holdings are affected by each news item.
""";

    private static final Set<String> MARKET_SCOUT_TOOLS = Set.of(
            "search_holdings", "get_portfolio_news", "search_news",
            "get_portfolio_summary", "get_sector_allocation"
    );

    @Tool(name = "market_scout",
          description = "Market intelligence: autonomously scans news for all portfolio holdings, identifies alerts and opportunities, and produces a briefing. Use when the user asks about market news, what's happening with their stocks, or portfolio alerts.")
    public Map<String, Object> marketScout(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Scan the market for news relevant to the user's portfolio. "
                    + "Check news for all holdings, identify alerts, opportunities, and risks.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(MARKET_SCOUT_PROMPT, task, MARKET_SCOUT_TOOLS, 4, userId, toolClient);

            return Map.of("analysis", result);
        } catch (Exception e) {
            return Map.of("error", "Market scan failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SIP OPTIMIZER AGENT
    // ═══════════════════════════════════════════════════════════

    private static final String SIP_OPTIMIZER_PROMPT = """
You are a SIP Optimizer AI agent. You analyze and optimize Systematic Investment Plans.

Your job:
1. Get SIP calendar to see all current SIPs
2. Search holdings to see fund performance
3. Get portfolio summary and asset allocation
4. Get goals to align SIPs with goals
5. Get rebalancing suggestions

After gathering data, produce a SIP optimization report:
- **Current SIPs**: All active SIPs with amounts, dates, fund names
- **Performance Review**: How each SIP fund has performed
- **Missed SIPs**: Any SIPs that were missed recently
- **Optimization Suggestions**: Increase/decrease/stop specific SIPs with reasoning
- **New SIP Recommendations**: Funds to start SIP in based on goals and allocation gaps
- **Monthly SIP Budget**: Recommended total monthly SIP and per-fund split

Use INR. Be specific about fund names and amounts.
""";

    private static final Set<String> SIP_OPTIMIZER_TOOLS = Set.of(
            "get_sip_calendar", "search_holdings", "get_portfolio_summary",
            "get_asset_allocation", "get_goals", "get_financial_health_score",
            "get_rebalancing_suggestions"
    );

    @Tool(name = "sip_optimizer",
          description = "SIP optimization: autonomously reviews all SIPs, checks fund performance, identifies missed SIPs, and suggests changes. Use when the user asks about SIPs, monthly investments, or fund optimization.")
    public Map<String, Object> sipOptimizer(
            @ToolParam(description = "User ID (UUID)") String userId) {
        try {
            String task = "Review all SIPs and systematic investments. Check fund performance, "
                    + "identify missed SIPs, and suggest optimizations including new SIPs to start.";

            McpToolClient toolClient = AgentContext.getToolClient();
            if (toolClient == null) return Map.of("error", "Agent context not available");
            String result = agentExecutor.execute(SIP_OPTIMIZER_PROMPT, task, SIP_OPTIMIZER_TOOLS, 4, userId, toolClient);

            return Map.of("analysis", result);
        } catch (Exception e) {
            return Map.of("error", "SIP optimization failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}
