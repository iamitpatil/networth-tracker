-- Add logo URLs to reference data metadata for banks, brokers, card issuers
-- Using clearbit logo API (freely available, no auth needed): https://logo.clearbit.com/{domain}
-- Fallback: Google favicon service: https://www.google.com/s2/favicons?domain={domain}&sz=64

-- ═══ PUBLIC SECTOR BANKS ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/sbi.co.in"') WHERE category = 'BANK' AND value = 'State Bank of India';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/bankofbaroda.in"') WHERE category = 'BANK' AND value = 'Bank of Baroda';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/bankofindia.co.in"') WHERE category = 'BANK' AND value = 'Bank of India';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/bankofmaharashtra.in"') WHERE category = 'BANK' AND value = 'Bank of Maharashtra';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/canarabank.com"') WHERE category = 'BANK' AND value = 'Canara Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/centralbankofindia.co.in"') WHERE category = 'BANK' AND value = 'Central Bank of India';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/indianbank.in"') WHERE category = 'BANK' AND value = 'Indian Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/iob.in"') WHERE category = 'BANK' AND value = 'Indian Overseas Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/psbindia.com"') WHERE category = 'BANK' AND value = 'Punjab & Sind Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/pnbindia.in"') WHERE category = 'BANK' AND value = 'Punjab National Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/ucobank.com"') WHERE category = 'BANK' AND value = 'UCO Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/unionbankofindia.co.in"') WHERE category = 'BANK' AND value = 'Union Bank of India';

-- ═══ PRIVATE SECTOR BANKS ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/axisbank.com"') WHERE category = 'BANK' AND value = 'Axis Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/bandhanbank.com"') WHERE category = 'BANK' AND value = 'Bandhan Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/csb.co.in"') WHERE category = 'BANK' AND value = 'CSB Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/cityunionbank.com"') WHERE category = 'BANK' AND value = 'City Union Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/dcbbank.com"') WHERE category = 'BANK' AND value = 'DCB Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/dfrbank.com"') WHERE category = 'BANK' AND value = 'Dhanlaxmi Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/federalbank.co.in"') WHERE category = 'BANK' AND value = 'Federal Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/hdfcbank.com"') WHERE category = 'BANK' AND value = 'HDFC Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/icicibank.com"') WHERE category = 'BANK' AND value = 'ICICI Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/indusind.com"') WHERE category = 'BANK' AND value = 'IndusInd Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/idfcfirstbank.com"') WHERE category = 'BANK' AND value = 'IDFC First Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/jkbank.com"') WHERE category = 'BANK' AND value = 'Jammu & Kashmir Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/karnatakabank.com"') WHERE category = 'BANK' AND value = 'Karnataka Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/kvb.co.in"') WHERE category = 'BANK' AND value = 'Karur Vysya Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/kotak.com"') WHERE category = 'BANK' AND value = 'Kotak Mahindra Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/rblbank.com"') WHERE category = 'BANK' AND value = 'RBL Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/southindianbank.com"') WHERE category = 'BANK' AND value = 'South Indian Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/yesbank.in"') WHERE category = 'BANK' AND value = 'Yes Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/idbibank.in"') WHERE category = 'BANK' AND value = 'IDBI Bank';

-- ═══ SMALL FINANCE BANKS ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/aubank.in"') WHERE category = 'BANK' AND value = 'AU Small Finance Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/equitasbank.com"') WHERE category = 'BANK' AND value = 'Equitas Small Finance Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/ujjivansfb.in"') WHERE category = 'BANK' AND value = 'Ujjivan Small Finance Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/janabank.com"') WHERE category = 'BANK' AND value = 'Jana Small Finance Bank';

-- ═══ PAYMENT BANKS ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/airtel.in"') WHERE category = 'BANK' AND value = 'Airtel Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/indiapost.gov.in"') WHERE category = 'BANK' AND value = 'India Post Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/paytm.com"') WHERE category = 'BANK' AND value = 'Paytm Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/jio.com"') WHERE category = 'BANK' AND value = 'Jio Payments Bank';

-- ═══ FOREIGN BANKS ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/sc.com"') WHERE category = 'BANK' AND value = 'Standard Chartered Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/hsbc.co.in"') WHERE category = 'BANK' AND value = 'HSBC';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/citibank.com"') WHERE category = 'BANK' AND value = 'Citibank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/db.com"') WHERE category = 'BANK' AND value = 'Deutsche Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/dbs.com"') WHERE category = 'BANK' AND value = 'DBS Bank India';

-- India Post
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata, '{}'), '{logo}', '"https://logo.clearbit.com/indiapost.gov.in"') WHERE category = 'BANK' AND value = 'India Post';

-- ═══ BROKERS ═══
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/zerodha.com"}' WHERE category = 'BROKER' AND value = 'Zerodha';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/groww.in"}' WHERE category = 'BROKER' AND value = 'Groww';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/angelone.in"}' WHERE category = 'BROKER' AND value = 'Angel One';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/upstox.com"}' WHERE category = 'BROKER' AND value = 'Upstox';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/icicidirect.com"}' WHERE category = 'BROKER' AND value = 'ICICI Direct';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/hdfcsec.com"}' WHERE category = 'BROKER' AND value = 'HDFC Securities';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/kotaksecurities.com"}' WHERE category = 'BROKER' AND value = 'Kotak Securities';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/5paisa.com"}' WHERE category = 'BROKER' AND value = '5Paisa';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/motilaloswal.com"}' WHERE category = 'BROKER' AND value = 'Motilal Oswal';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/sharekhan.com"}' WHERE category = 'BROKER' AND value = 'Sharekhan';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/paytmmoney.com"}' WHERE category = 'BROKER' AND value = 'Paytm Money';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/dhan.co"}' WHERE category = 'BROKER' AND value = 'Dhan';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/indmoney.com"}' WHERE category = 'BROKER' AND value = 'INDmoney';

-- ═══ CARD ISSUERS (reuse bank logos where applicable) ═══
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/hdfcbank.com"}' WHERE category = 'CARD_ISSUER' AND value = 'HDFC Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/icicibank.com"}' WHERE category = 'CARD_ISSUER' AND value = 'ICICI Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/sbicard.com"}' WHERE category = 'CARD_ISSUER' AND value = 'SBI Card';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/axisbank.com"}' WHERE category = 'CARD_ISSUER' AND value = 'Axis Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/kotak.com"}' WHERE category = 'CARD_ISSUER' AND value = 'Kotak Mahindra Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/idfcfirstbank.com"}' WHERE category = 'CARD_ISSUER' AND value = 'IDFC First Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/indusind.com"}' WHERE category = 'CARD_ISSUER' AND value = 'IndusInd Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/rblbank.com"}' WHERE category = 'CARD_ISSUER' AND value = 'RBL Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/yesbank.in"}' WHERE category = 'CARD_ISSUER' AND value = 'Yes Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/americanexpress.com"}' WHERE category = 'CARD_ISSUER' AND value = 'American Express';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/sc.com"}' WHERE category = 'CARD_ISSUER' AND value = 'Standard Chartered';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/hsbc.co.in"}' WHERE category = 'CARD_ISSUER' AND value = 'HSBC';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/aubank.in"}' WHERE category = 'CARD_ISSUER' AND value = 'AU Small Finance Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/federalbank.co.in"}' WHERE category = 'CARD_ISSUER' AND value = 'Federal Bank';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/bankofbaroda.in"}' WHERE category = 'CARD_ISSUER' AND value = 'Bank of Baroda';

-- ═══ CARD NETWORKS ═══
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/visa.com"}' WHERE category = 'CARD_NETWORK' AND value = 'VISA';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/mastercard.com"}' WHERE category = 'CARD_NETWORK' AND value = 'Mastercard';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/rupay.co.in"}' WHERE category = 'CARD_NETWORK' AND value = 'RuPay';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/americanexpress.com"}' WHERE category = 'CARD_NETWORK' AND value = 'AMEX';
UPDATE reference_data SET metadata = '{"logo":"https://logo.clearbit.com/dinersclub.com"}' WHERE category = 'CARD_NETWORK' AND value = 'Diners Club';
