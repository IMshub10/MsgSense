# Bank Balance Service Registry

Registry version: `2026-06-09`

NotifAI never calls or sends an SMS directly for balance refresh. An actionable entry only
opens the system dialer or SMS composer after user confirmation. The reported balance changes
only after a later bank SMS is received and extracted locally.

## Verified actionable banks

| Bank | Method | Official source |
|---|---|---|
| State Bank of India | Missed-call dialer | https://sbi.co.in/web/personal-banking/information-services/kyc-guidelines/sbi-quick-missed-call-banking |
| HDFC Bank | Missed-call dialer | https://v.hdfcbank.com/htdocs/common/account-balance/index.html |
| ICICI Bank | Missed-call dialer | https://www.icicibank.com/mobile-banking/sms-keywords |
| Axis Bank | Missed-call dialer | https://www.axisbank.com/bank-smart/toll-free-axis-dial/toll-free-axis-dial |
| Kotak Mahindra Bank | Missed-call dialer | https://www.kotak.com/en/help-center/bank-account/account-balance-related/how-can-check-my-account-balance-.html |
| Punjab National Bank | Missed-call dialer | https://www.pnbindia.in/Missed-Call.html |
| Indian Bank | Missed-call dialer | https://www.indianbank.in/departments/online-customer-complaints/ |
| RBL Bank | Missed-call dialer | https://www.rblbank.com/personal-banking/accounts/savings-accounts/seniors-first-savings-account |
| IndusInd Bank | Missed-call dialer | https://www.indusind.com/in/en/personal/mobile-banking-services/missed-call-banking.html |
| YES Bank | Missed-call dialer | https://www.yesbank.in/content/published/api/v1.1/assets/CONTCB630B88C70B400FA193D8A26432CAE6/native/regulatorypolicies_citizencharter_pdf1.pdf |
All other logo-backed registry entries intentionally use `NONE` until a current,
unambiguous official source is reviewed. Bank of Baroda, Canara Bank, and Union Bank of
India remain visible in the account UI but fail closed for refresh.

Review every actionable entry before release and whenever its source is older than 180 days.
