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
}
