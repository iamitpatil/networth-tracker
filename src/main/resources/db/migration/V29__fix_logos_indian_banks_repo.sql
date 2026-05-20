-- Use high-quality SVG logos from https://github.com/praveenpuglia/indian-banks
-- Pattern: https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/{slug}/symbol.svg
-- For banks not in that repo, use gstatic favicon as fallback

-- Clear old (broken clearbit) logos
UPDATE reference_data SET metadata = metadata - 'logo' WHERE metadata->>'logo' LIKE '%clearbit%';

-- ═══ BANKS — from praveenpuglia/indian-banks repo (SVG symbols) ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/sbin/symbol.svg"') WHERE category='BANK' AND value='State Bank of India';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/barb/symbol.svg"') WHERE category='BANK' AND value='Bank of Baroda';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/bkid/symbol.svg"') WHERE category='BANK' AND value='Bank of India';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/mahb/symbol.svg"') WHERE category='BANK' AND value='Bank of Maharashtra';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/cnrb/symbol.svg"') WHERE category='BANK' AND value='Canara Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/cbin/symbol.svg"') WHERE category='BANK' AND value='Central Bank of India';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/idib/symbol.svg"') WHERE category='BANK' AND value='Indian Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ioba/symbol.svg"') WHERE category='BANK' AND value='Indian Overseas Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/psib/symbol.svg"') WHERE category='BANK' AND value='Punjab & Sind Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/punb/symbol.svg"') WHERE category='BANK' AND value='Punjab National Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ucba/symbol.svg"') WHERE category='BANK' AND value='UCO Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ubin/symbol.svg"') WHERE category='BANK' AND value='Union Bank of India';

-- Private Banks
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/utib/symbol.svg"') WHERE category='BANK' AND value='Axis Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/bdbl/symbol.svg"') WHERE category='BANK' AND value='Bandhan Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/csbk/symbol.svg"') WHERE category='BANK' AND value='CSB Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ciub/symbol.svg"') WHERE category='BANK' AND value='City Union Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/dcbl/symbol.svg"') WHERE category='BANK' AND value='DCB Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/dlxb/symbol.svg"') WHERE category='BANK' AND value='Dhanlaxmi Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/fdrl/symbol.svg"') WHERE category='BANK' AND value='Federal Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/hdfc/symbol.svg"') WHERE category='BANK' AND value='HDFC Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/icic/symbol.svg"') WHERE category='BANK' AND value='ICICI Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/indb/symbol.svg"') WHERE category='BANK' AND value='IndusInd Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/idfb/symbol.svg"') WHERE category='BANK' AND value='IDFC First Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/jaka/symbol.svg"') WHERE category='BANK' AND value='Jammu & Kashmir Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/karb/symbol.svg"') WHERE category='BANK' AND value='Karnataka Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/kvbl/symbol.svg"') WHERE category='BANK' AND value='Karur Vysya Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/kkbk/symbol.svg"') WHERE category='BANK' AND value='Kotak Mahindra Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ntbl/symbol.svg"') WHERE category='BANK' AND value='Nainital Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ratn/symbol.svg"') WHERE category='BANK' AND value='RBL Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/sibl/symbol.svg"') WHERE category='BANK' AND value='South Indian Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/tmbl/symbol.svg"') WHERE category='BANK' AND value='Tamilnad Mercantile Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/yesb/symbol.svg"') WHERE category='BANK' AND value='Yes Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ibkl/symbol.svg"') WHERE category='BANK' AND value='IDBI Bank';

-- SFBs
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/aubl/symbol.svg"') WHERE category='BANK' AND value='AU Small Finance Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/esmf/symbol.svg"') WHERE category='BANK' AND value='ESAF Small Finance Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ujvn/symbol.svg"') WHERE category='BANK' AND value='Ujjivan Small Finance Bank';

-- Payment Banks
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/airp/symbol.svg"') WHERE category='BANK' AND value='Airtel Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/fino/symbol.svg"') WHERE category='BANK' AND value='Fino Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/pytm/symbol.svg"') WHERE category='BANK' AND value='Paytm Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/jiop/symbol.svg"') WHERE category='BANK' AND value='Jio Payments Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/scbl/symbol.svg"') WHERE category='BANK' AND value='Standard Chartered Bank';

-- ═══ CARD ISSUERS — reuse bank logos where matching ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/hdfc/symbol.svg"') WHERE category='CARD_ISSUER' AND value='HDFC Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/icic/symbol.svg"') WHERE category='CARD_ISSUER' AND value='ICICI Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/sbin/symbol.svg"') WHERE category='CARD_ISSUER' AND value='SBI Card';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/utib/symbol.svg"') WHERE category='CARD_ISSUER' AND value='Axis Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/kkbk/symbol.svg"') WHERE category='CARD_ISSUER' AND value='Kotak Mahindra Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/idfb/symbol.svg"') WHERE category='CARD_ISSUER' AND value='IDFC First Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/indb/symbol.svg"') WHERE category='CARD_ISSUER' AND value='IndusInd Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/ratn/symbol.svg"') WHERE category='CARD_ISSUER' AND value='RBL Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/yesb/symbol.svg"') WHERE category='CARD_ISSUER' AND value='Yes Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/aubl/symbol.svg"') WHERE category='CARD_ISSUER' AND value='AU Small Finance Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/fdrl/symbol.svg"') WHERE category='CARD_ISSUER' AND value='Federal Bank';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/barb/symbol.svg"') WHERE category='CARD_ISSUER' AND value='Bank of Baroda';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/scbl/symbol.svg"') WHERE category='CARD_ISSUER' AND value='Standard Chartered';

-- ═══ Remaining items without indian-banks repo logos: use gstatic fallback ═══
-- Foreign banks not in indian-banks repo
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://hsbc.co.in&size=128"') WHERE category IN ('BANK','CARD_ISSUER') AND value='HSBC' AND (metadata->>'logo' IS NULL OR metadata->>'logo' = 'null');
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://citibank.com&size=128"') WHERE category IN ('BANK','CARD_ISSUER') AND value='Citibank' AND (metadata->>'logo' IS NULL OR metadata->>'logo' = 'null');
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://americanexpress.com&size=128"') WHERE category='CARD_ISSUER' AND value='American Express' AND (metadata->>'logo' IS NULL OR metadata->>'logo' = 'null');
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://dbs.com&size=128"') WHERE category='BANK' AND value='DBS Bank India' AND (metadata->>'logo' IS NULL OR metadata->>'logo' = 'null');
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://db.com&size=128"') WHERE category='BANK' AND value='Deutsche Bank' AND (metadata->>'logo' IS NULL OR metadata->>'logo' = 'null');
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://indiapost.gov.in&size=128"') WHERE category='BANK' AND value IN ('India Post', 'India Post Payments Bank') AND (metadata->>'logo' IS NULL OR metadata->>'logo' = 'null');

-- ═══ BROKERS — gstatic fallback (no indian-banks repo coverage) ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://zerodha.com&size=128"') WHERE category='BROKER' AND value='Zerodha';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://groww.in&size=128"') WHERE category='BROKER' AND value='Groww';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://angelone.in&size=128"') WHERE category='BROKER' AND value='Angel One';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://upstox.com&size=128"') WHERE category='BROKER' AND value='Upstox';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://icicidirect.com&size=128"') WHERE category='BROKER' AND value='ICICI Direct';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://hdfcsec.com&size=128"') WHERE category='BROKER' AND value='HDFC Securities';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://kotaksecurities.com&size=128"') WHERE category='BROKER' AND value='Kotak Securities';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://5paisa.com&size=128"') WHERE category='BROKER' AND value='5Paisa';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://motilaloswal.com&size=128"') WHERE category='BROKER' AND value='Motilal Oswal';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://sharekhan.com&size=128"') WHERE category='BROKER' AND value='Sharekhan';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://paytmmoney.com&size=128"') WHERE category='BROKER' AND value='Paytm Money';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://dhan.co&size=128"') WHERE category='BROKER' AND value='Dhan';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://indmoney.com&size=128"') WHERE category='BROKER' AND value='INDmoney';

-- ═══ CARD NETWORKS — gstatic ═══
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://visa.com&size=128"') WHERE category='CARD_NETWORK' AND value='VISA';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://mastercard.com&size=128"') WHERE category='CARD_NETWORK' AND value='Mastercard';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://rupay.co.in&size=128"') WHERE category='CARD_NETWORK' AND value='RuPay';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://americanexpress.com&size=128"') WHERE category='CARD_NETWORK' AND value='AMEX';
UPDATE reference_data SET metadata = jsonb_set(COALESCE(metadata,'{}'),'{logo}','"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://dinersclub.com&size=128"') WHERE category='CARD_NETWORK' AND value='Diners Club';
