package com.networth.repository;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The projection the scheduled price sweep iterates.
 *
 * <p>Worth a test of its own because an interface projection over a JPQL {@code SELECT DISTINCT} of
 * three columns fails at runtime rather than at compile time if an alias stops matching a getter --
 * and that failure would take out every price update in the application at once, quietly, with the
 * holdings simply never changing again.
 */
@DataJpaTest
@ActiveProfiles("test")
class HoldingRepositoryHeldSymbolTest {

    @Autowired
    private HoldingRepository holdingRepository;

    @Test
    @DisplayName("one instrument held many times over is one row to price")
    void duplicatesCollapse() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        // The same stock in two demat accounts, and again under another family member: five holdings
        // and one quote to fetch. The old sweep fetched it once per holding.
        holdingRepository.save(holding(alice, "INFY", AssetType.EQUITY, null, UUID.randomUUID()));
        holdingRepository.save(holding(alice, "INFY", AssetType.EQUITY, null, UUID.randomUUID()));
        holdingRepository.save(holding(bob, "INFY", AssetType.EQUITY, null, UUID.randomUUID()));
        holdingRepository.save(holding(alice, "TCS", AssetType.EQUITY, null, null));
        holdingRepository.save(holding(bob, "AXISBLUECHIP", AssetType.MUTUAL_FUND, "INF846K01EW2", null));
        holdingRepository.flush();

        assertThat(holdingRepository.findDistinctHeldSymbols())
                .extracting(
                        HoldingRepository.HeldSymbol::getSymbol,
                        HoldingRepository.HeldSymbol::getAssetType,
                        HoldingRepository.HeldSymbol::getIsin)
                .containsExactlyInAnyOrder(
                        tuple("INFY", AssetType.EQUITY, null),
                        tuple("TCS", AssetType.EQUITY, null),
                        tuple("AXISBLUECHIP", AssetType.MUTUAL_FUND, "INF846K01EW2"));
    }

    @Test
    @DisplayName("a sold-out holding is not worth a provider request")
    void softDeletedHoldingsAreExcluded() {
        UUID alice = UUID.randomUUID();
        Holding sold = holding(alice, "YESBANK", AssetType.EQUITY, null, null);
        sold.setDeletedAt(Instant.now());
        holdingRepository.save(sold);
        holdingRepository.save(holding(alice, "INFY", AssetType.EQUITY, null, null));
        holdingRepository.flush();

        assertThat(holdingRepository.findDistinctHeldSymbols())
                .extracting(HoldingRepository.HeldSymbol::getSymbol)
                .containsExactly("INFY");
        assertThat(holdingRepository.findAllActive())
                .extracting(Holding::getSymbol)
                .containsExactly("INFY");
    }

    @Test
    @DisplayName("the same symbol under two asset types is two instruments")
    void assetTypeIsPartOfTheIdentity() {
        UUID alice = UUID.randomUUID();
        // A sovereign gold bond and physical gold can share a name and are priced from different
        // sources, so the asset type is part of what identifies a quote.
        holdingRepository.save(holding(alice, "SGBAUG28", AssetType.SGB, null, null));
        holdingRepository.save(holding(alice, "SGBAUG28", AssetType.GOLD, null, null));
        holdingRepository.flush();

        assertThat(holdingRepository.findDistinctHeldSymbols()).hasSize(2);
    }

    private Holding holding(UUID userId, String symbol, AssetType assetType, String isin, UUID dematId) {
        return Holding.builder()
                .userId(userId)
                .symbol(symbol)
                .assetType(assetType)
                .isin(isin)
                .dematAccountId(dematId)
                .name(symbol)
                .quantity(new BigDecimal("10"))
                .averageBuyPrice(new BigDecimal("100.00"))
                .build();
    }
}
