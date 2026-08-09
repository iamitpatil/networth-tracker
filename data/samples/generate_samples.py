#!/usr/bin/env python3
"""
Generate sample PDF documents for testing AI document parsing.
Run: python3 data/samples/generate_samples.py

Generates:
  1. credit_card_bill.pdf         — HDFC credit card statement
  2. salary_slip.pdf              — Monthly salary slip
  3. bank_statement.pdf           — SBI savings account statement
  4. nps_statement.pdf            — NPS Transaction Statement (no password)
  5. nps_statement_protected.pdf  — NPS Transaction Statement (password: test1234)
  6. form16.pdf                   — Form 16 tax document
"""

import os
from io import BytesIO
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm, cm
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from PyPDF2 import PdfReader, PdfWriter

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
styles = getSampleStyleSheet()
bold = ParagraphStyle('Bold', parent=styles['Normal'], fontName='Helvetica-Bold', fontSize=10)
small = ParagraphStyle('Small', parent=styles['Normal'], fontSize=8, textColor=colors.grey)
heading = ParagraphStyle('H', parent=styles['Heading1'], fontSize=14, spaceAfter=6)
subheading = ParagraphStyle('SH', parent=styles['Heading2'], fontSize=11, spaceAfter=4)


def save_pdf(elements, filename, password=None):
    buf = BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A4, topMargin=2*cm, bottomMargin=2*cm)
    doc.build(elements)
    buf.seek(0)

    if password:
        reader = PdfReader(buf)
        writer = PdfWriter()
        for page in reader.pages:
            writer.add_page(page)
        writer.encrypt(password)
        path = os.path.join(OUT_DIR, filename)
        with open(path, 'wb') as f:
            writer.write(f)
    else:
        path = os.path.join(OUT_DIR, filename)
        with open(path, 'wb') as f:
            f.write(buf.read())

    print(f"  Created: {filename}" + (f" (password: {password})" if password else ""))


# ── 1. Credit Card Bill ──
def gen_credit_card_bill():
    e = []
    e.append(Paragraph("HDFC Bank Credit Card Statement", heading))
    e.append(Paragraph("Statement Period: 01-Apr-2025 to 30-Apr-2025", styles['Normal']))
    e.append(Spacer(1, 8))

    info = [
        ["Card Number", "XXXX XXXX XXXX 4532"],
        ["Card Type", "VISA Regalia"],
        ["Statement Date", "2025-04-30"],
        ["Due Date", "2025-05-20"],
        ["Minimum Amount Due", "Rs. 2,450.00"],
        ["Total Amount Due", "Rs. 48,932.56"],
        ["Previous Balance", "Rs. 12,340.00"],
        ["Payments Received", "Rs. 12,340.00"],
        ["New Charges", "Rs. 48,932.56"],
    ]
    t = Table(info, colWidths=[150, 250])
    t.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 9),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    e.append(Paragraph("Transaction Details", subheading))
    txn_data = [
        ["Date", "Description", "Amount (Rs.)"],
        ["02-Apr-2025", "Amazon.in - Electronics", "12,499.00"],
        ["05-Apr-2025", "Swiggy - Food Delivery", "856.00"],
        ["07-Apr-2025", "IRCTC - Train Ticket", "2,340.00"],
        ["10-Apr-2025", "Reliance Smart - Groceries", "4,521.00"],
        ["12-Apr-2025", "Netflix India - Subscription", "649.00"],
        ["15-Apr-2025", "Flipkart - Clothing", "3,299.00"],
        ["18-Apr-2025", "Shell Petrol Pump - Fuel", "3,200.00"],
        ["20-Apr-2025", "Apollo Pharmacy - Medical", "1,890.00"],
        ["22-Apr-2025", "Zomato - Restaurant", "1,456.00"],
        ["25-Apr-2025", "DMart - Household", "5,678.00"],
        ["27-Apr-2025", "BookMyShow - Entertainment", "1,200.00"],
        ["28-Apr-2025", "Uber - Transport", "987.56"],
        ["29-Apr-2025", "BigBasket - Groceries", "6,234.00"],
        ["30-Apr-2025", "Jio Recharge - Telecom", "399.00"],
    ]
    t = Table(txn_data, colWidths=[80, 250, 100])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1a365d')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#f7fafc')]),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('ALIGN', (2, 0), (2, -1), 'RIGHT'),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    e.append(Paragraph("Spend Summary", subheading))
    summary = [
        ["Category", "Amount (Rs.)"],
        ["Shopping", "15,798.00"],
        ["Food & Dining", "2,312.00"],
        ["Groceries", "16,433.00"],
        ["Travel", "5,540.00"],
        ["Fuel", "3,200.00"],
        ["Medical", "1,890.00"],
        ["Entertainment", "1,849.00"],
        ["Utilities & Telecom", "1,048.00"],
    ]
    t = Table(summary, colWidths=[200, 120])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2d3748')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (1, 0), (1, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))
    e.append(Paragraph("This is a computer generated statement.", small))
    save_pdf(e, "credit_card_bill.pdf")


# ── 2. Salary Slip ──
def gen_salary_slip():
    e = []
    e.append(Paragraph("SALARY SLIP - April 2025", heading))
    e.append(Spacer(1, 4))

    info = [
        ["Employee Name", "Rajesh Kumar Sharma", "Employee ID", "EMP-10234"],
        ["Designation", "Senior Software Engineer", "Department", "Engineering"],
        ["PAN", "ABCPS1234E", "Bank A/C", "XXXX5678"],
        ["Pay Date", "2025-04-30", "Working Days", "22"],
    ]
    t = Table(info, colWidths=[100, 150, 100, 120])
    t.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
        ('FONTNAME', (2, 0), (2, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 9),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.lightgrey),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    e.append(Paragraph("Earnings & Deductions", subheading))
    pay_data = [
        ["Earnings", "Amount (Rs.)", "Deductions", "Amount (Rs.)"],
        ["Basic Salary", "45,000.00", "Provident Fund (PF)", "5,400.00"],
        ["House Rent Allowance", "22,500.00", "Professional Tax", "200.00"],
        ["Special Allowance", "18,000.00", "Income Tax (TDS)", "8,750.00"],
        ["Conveyance Allowance", "1,600.00", "Health Insurance", "1,200.00"],
        ["Medical Allowance", "1,250.00", "", ""],
        ["LTA", "3,750.00", "", ""],
        ["Performance Bonus", "5,000.00", "", ""],
        ["", "", "", ""],
        ["Gross Earnings", "97,100.00", "Total Deductions", "15,550.00"],
    ]
    t = Table(pay_data, colWidths=[130, 100, 130, 100])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1a365d')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTNAME', (0, -1), (-1, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (1, 0), (1, -1), 'RIGHT'),
        ('ALIGN', (3, 0), (3, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('BACKGROUND', (0, -1), (-1, -1), colors.HexColor('#edf2f7')),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    net = [
        ["Net Pay", "Rs. 81,550.00"],
        ["Net Pay (in words)", "Rupees Eighty One Thousand Five Hundred Fifty Only"],
    ]
    t = Table(net, colWidths=[130, 330])
    t.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (-1, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 10),
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor('#ebf8ff')),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))
    e.append(Paragraph("Employer: TechVista Solutions Private Limited", small))
    save_pdf(e, "salary_slip.pdf")


# ── 3. Bank Statement ──
def gen_bank_statement():
    e = []
    e.append(Paragraph("State Bank of India - Account Statement", heading))
    e.append(Spacer(1, 4))

    info = [
        ["Account Holder", "Rajesh Kumar Sharma"],
        ["Account Number", "38764510982"],
        ["Account Type", "SAVINGS"],
        ["Branch", "Pune Kothrud Branch"],
        ["IFSC", "SBIN0000123"],
        ["Statement Period", "01-Apr-2025 to 30-Apr-2025"],
        ["Opening Balance", "Rs. 1,45,230.50"],
        ["Closing Balance", "Rs. 2,12,680.50"],
    ]
    t = Table(info, colWidths=[130, 300])
    t.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 9),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    e.append(Paragraph("Transaction Details", subheading))
    txns = [
        ["Date", "Description", "Debit (Rs.)", "Credit (Rs.)", "Balance (Rs.)"],
        ["01-Apr-2025", "Opening Balance", "", "", "1,45,230.50"],
        ["02-Apr-2025", "NEFT-RENT-APR25", "15,000.00", "", "1,30,230.50"],
        ["05-Apr-2025", "UPI/Swiggy/456789", "856.00", "", "1,29,374.50"],
        ["07-Apr-2025", "SALARY-APR2025-TECHVISTA", "", "81,550.00", "2,10,924.50"],
        ["10-Apr-2025", "EMI-HDFC-HOME-LOAN", "22,450.00", "", "1,88,474.50"],
        ["12-Apr-2025", "UPI/DMart/789012", "4,521.00", "", "1,83,953.50"],
        ["15-Apr-2025", "ATM/CASH/JALGAON", "5,000.00", "", "1,78,953.50"],
        ["18-Apr-2025", "AUTOPAY/LIC/PREMIUM", "3,500.00", "", "1,75,453.50"],
        ["20-Apr-2025", "UPI/GooglePay/DIVID", "", "12,500.00", "1,87,953.50"],
        ["22-Apr-2025", "SIP/HDFC-MF/EQUITY", "10,000.00", "", "1,77,953.50"],
        ["25-Apr-2025", "NEFT-ELECTRICITY-APR", "2,340.00", "", "1,75,613.50"],
        ["27-Apr-2025", "NPS-CONTRIBUTION-APR", "5,000.00", "", "1,70,613.50"],
        ["28-Apr-2025", "UPI/FREELANCE/CLIENT", "", "45,000.00", "2,15,613.50"],
        ["29-Apr-2025", "UPI/Petrol/Shell", "2,933.00", "", "2,12,680.50"],
        ["30-Apr-2025", "Closing Balance", "", "", "2,12,680.50"],
    ]
    t = Table(txns, colWidths=[70, 170, 75, 75, 80])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1e40af')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 7),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#f0f4ff')]),
        ('ALIGN', (2, 0), (4, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))
    e.append(Paragraph("This is a computer generated statement and does not require signature.", small))
    save_pdf(e, "bank_statement.pdf")


# ── 4 & 5. NPS Statement ──
def gen_nps_statement(protected=False):
    e = []
    e.append(Paragraph("NPS TRANSACTION STATEMENT", heading))
    e.append(Paragraph("Apr 01, 2025 To Apr 30, 2025", styles['Normal']))
    e.append(Paragraph("Statement Generation Date: May 05, 2025 08:30 AM", small))
    e.append(Spacer(1, 4))

    e.append(Paragraph("NPS Transaction Statement for Tier I Account", subheading))
    info = [
        ["PRAN", "110078945612", "Registration Date", "15-Mar-21"],
        ["Subscriber Name", "Rajesh Kumar Sharma", "Tier I Status", "Active"],
        ["Mobile Number", "9876543210", "Email ID", "rajesh.sharma@example.com"],
        ["Employer", "TechVista Solutions Pvt Ltd", "IRA Status", "IRA Compliant"],
    ]
    t = Table(info, colWidths=[100, 150, 100, 120])
    t.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
        ('FONTNAME', (2, 0), (2, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.lightgrey),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))

    e.append(Paragraph("Scheme Choice: ACTIVE CHOICE", bold))
    schemes = [
        ["Investment Option", "Scheme Details", "Percentage"],
        ["Scheme 1", "SBI PENSION FUND SCHEME E - TIER I", "50.00%"],
        ["Scheme 2", "SBI PENSION FUND SCHEME C - TIER I", "30.00%"],
        ["Scheme 3", "SBI PENSION FUND SCHEME G - TIER I", "20.00%"],
    ]
    t = Table(schemes, colWidths=[100, 250, 70])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2d3748')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))

    e.append(Paragraph("Investment Summary", subheading))
    summary = [
        ["Value of Holdings (Rs.)", "Total Contributions (Rs.)", "Total Withdrawal (Rs.)", "Notional Gain (Rs.)", "XIRR"],
        ["7,85,432.50", "6,25,000.00", "0.00", "1,60,432.50", "12.45%"],
    ]
    t = Table(summary, colWidths=[110, 110, 100, 100, 50])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1a365d')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (0, 1), (-1, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))

    e.append(Paragraph("Scheme Wise Summary", subheading))
    scheme_summary = [
        ["Particulars", "SBI Scheme E - Tier I", "SBI Scheme C - Tier I", "SBI Scheme G - Tier I"],
        ["Value of Holdings (Rs.)", "3,92,716.25", "2,35,629.75", "1,57,086.50"],
        ["Total Units", "7,072.5400", "5,215.4200", "3,920.8100"],
        ["NAV as on 30-Apr-25", "55.5300", "45.1700", "40.0600"],
    ]
    t = Table(scheme_summary, colWidths=[120, 120, 120, 120])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#4a5568')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 7),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (1, 1), (-1, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))

    e.append(Paragraph("Contribution Details", subheading))
    contributions = [
        ["Date", "Particulars", "Employee (Rs.)", "Employer (Rs.)", "Total (Rs.)"],
        ["05-Apr-2025", "For March, 2025", "5,000.00", "7,500.00", "12,500.00"],
    ]
    t = Table(contributions, colWidths=[80, 140, 80, 80, 80])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2d3748')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (2, 1), (-1, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))

    e.append(Paragraph("Transaction Details", subheading))
    txns = [
        ["Date", "Particulars", "Scheme E Amt / NAV / Units", "Scheme C Amt / NAV / Units", "Scheme G Amt / NAV / Units"],
        ["01-Apr-25", "Opening balance", "6,960.1200", "5,115.6800", "3,840.2900"],
        ["05-Apr-25", "Contribution Mar'25", "6,250.00 / 55.48 / 112.6500", "3,750.00 / 45.12 / 83.1200", "2,500.00 / 39.98 / 62.5300"],
        ["30-Apr-25", "Closing Balance", "7,072.5400", "5,215.4200", "3,920.8100"],
    ]
    t = Table(txns, colWidths=[60, 100, 120, 120, 120])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2d3748')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 7),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    e.append(t)
    e.append(Spacer(1, 8))

    e.append(Paragraph("Nominee Name/s: PRIYA SHARMA (50%), SURESH KUMAR SHARMA (50%)", small))
    e.append(Paragraph("This is computer generated statement and does not require any signature/stamp.", small))

    if protected:
        save_pdf(e, "nps_statement_protected.pdf", password="test1234")
    else:
        save_pdf(e, "nps_statement.pdf")


# ── 6. Form 16 ──
def gen_form16():
    e = []
    e.append(Paragraph("FORM No. 16", heading))
    e.append(Paragraph("Certificate under section 203 of the Income-tax Act, 1961 for tax deducted at source from income chargeable under the head 'Salaries'", small))
    e.append(Spacer(1, 8))

    info = [
        ["Assessment Year", "2025-26"],
        ["Name of Employer", "TechVista Solutions Private Limited"],
        ["TAN of Employer", "BLRI12345E"],
        ["PAN of Employer", "AABCI1234D"],
        ["Name of Employee", "Rajesh Kumar Sharma"],
        ["PAN of Employee", "ABCPS1234E"],
        ["Employee Address", "42, MG Road, Sector 15, Pune, Maharashtra"],
        ["Period of Employment", "01-Apr-2024 to 31-Mar-2025"],
    ]
    t = Table(info, colWidths=[140, 320])
    t.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (0, -1), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 9),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.lightgrey),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    e.append(Paragraph("Part B - Details of Salary and Tax", subheading))
    salary_data = [
        ["Particulars", "Amount (Rs.)"],
        ["1. Gross Salary", ""],
        ["   (a) Salary as per section 17(1)", "11,65,200.00"],
        ["   (b) Value of perquisites u/s 17(2)", "0.00"],
        ["   (c) Profits in lieu of salary u/s 17(3)", "0.00"],
        ["   (d) Total", "11,65,200.00"],
        ["2. Less: Allowances exempt u/s 10", ""],
        ["   (a) House Rent Allowance", "2,70,000.00"],
        ["   (b) Leave Travel Allowance", "45,000.00"],
        ["3. Balance (1d - 2)", "8,50,200.00"],
        ["4. Deductions u/s 16", ""],
        ["   (a) Standard Deduction u/s 16(ia)", "50,000.00"],
        ["   (b) Professional Tax u/s 16(iii)", "2,400.00"],
        ["5. Income chargeable under Salaries (3 - 4)", "7,97,800.00"],
        ["6. Income from other sources (declared)", "45,000.00"],
        ["7. Gross Total Income (5 + 6)", "8,42,800.00"],
        ["8. Deductions under Chapter VI-A", ""],
        ["   (a) Section 80C (PF + LIC + ELSS)", "1,50,000.00"],
        ["   (b) Section 80D (Medical Insurance)", "25,000.00"],
        ["   (c) Section 80CCD(1B) (NPS)", "50,000.00"],
        ["   (d) Total deductions", "2,25,000.00"],
        ["9. Total Taxable Income (7 - 8d)", "6,17,800.00"],
        ["10. Tax on Total Income", "33,560.00"],
        ["11. Surcharge", "0.00"],
        ["12. Education Cess (4%)", "1,342.00"],
        ["13. Total Tax Payable", "34,902.00"],
        ["14. Less: Relief u/s 87A", "0.00"],
        ["15. Tax Payable after Relief", "34,902.00"],
        ["16. TDS Deducted", "34,902.00"],
    ]
    t = Table(salary_data, colWidths=[320, 120])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1a365d')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (1, 0), (1, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))

    e.append(Paragraph("Quarter-wise TDS Details", subheading))
    tds = [
        ["Quarter", "Receipt No.", "Amount Deducted (Rs.)", "Amount Deposited (Rs.)", "Date"],
        ["Q1 (Apr-Jun)", "TDS/2024/Q1/12345", "8,726.00", "8,726.00", "15-Jul-2024"],
        ["Q2 (Jul-Sep)", "TDS/2024/Q2/12346", "8,726.00", "8,726.00", "15-Oct-2024"],
        ["Q3 (Oct-Dec)", "TDS/2024/Q3/12347", "8,725.00", "8,725.00", "15-Jan-2025"],
        ["Q4 (Jan-Mar)", "TDS/2024/Q4/12348", "8,725.00", "8,725.00", "15-Apr-2025"],
    ]
    t = Table(tds, colWidths=[80, 120, 100, 100, 80])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2d3748')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, -1), 7),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
        ('ALIGN', (2, 1), (3, -1), 'RIGHT'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    e.append(t)
    e.append(Spacer(1, 12))
    e.append(Paragraph("Verified. Signature of the person responsible for deduction of tax.", small))
    save_pdf(e, "form16.pdf")


# ── Generate all ──
if __name__ == "__main__":
    print("Generating sample PDFs...")
    gen_credit_card_bill()
    gen_salary_slip()
    gen_bank_statement()
    gen_nps_statement(protected=False)
    gen_nps_statement(protected=True)
    gen_form16()
    print(f"\nAll samples generated in: {OUT_DIR}/")
