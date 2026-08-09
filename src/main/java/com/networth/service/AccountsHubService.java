package com.networth.service;

import com.networth.model.entity.*;
import com.networth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsHubService {

    private final BankAccountRepository bankAccountRepository;
    private final DematAccountRepository dematAccountRepository;
    private final CreditCardRepository creditCardRepository;
    private final NpsAccountRepository npsAccountRepository;
    private final PpfAccountRepository ppfAccountRepository;
    private final EpfAccountRepository epfAccountRepository;

    /**
     * Get all financial accounts for a user, grouped by type.
     */
    public Map<String, Object> getAllAccounts(UUID userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bankAccounts", bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId));
        result.put("dematAccounts", dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId));
        result.put("creditCards", creditCardRepository.findByUserIdOrderByCreatedAtDesc(userId));
        result.put("npsAccounts", npsAccountRepository.findByUserIdOrderByCreatedAtDesc(userId));
        result.put("ppfAccounts", ppfAccountRepository.findByUserIdOrderByCreatedAtDesc(userId));
        result.put("epfAccounts", epfAccountRepository.findByUserIdOrderByCreatedAtDesc(userId));

        // Summary counts
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("bankAccounts", ((List<?>) result.get("bankAccounts")).size());
        counts.put("dematAccounts", ((List<?>) result.get("dematAccounts")).size());
        counts.put("creditCards", ((List<?>) result.get("creditCards")).size());
        counts.put("npsAccounts", ((List<?>) result.get("npsAccounts")).size());
        counts.put("ppfAccounts", ((List<?>) result.get("ppfAccounts")).size());
        counts.put("epfAccounts", ((List<?>) result.get("epfAccounts")).size());
        counts.put("total", counts.values().stream().mapToInt(Integer::intValue).sum());
        result.put("counts", counts);

        return result;
    }

    // ── Credit Cards ──

    @Transactional
    public CreditCard createCreditCard(UUID userId, CreditCard card) {
        card.setUserId(userId);
        return creditCardRepository.save(card);
    }

    @Transactional
    public CreditCard updateCreditCard(UUID userId, UUID id, CreditCard updates) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credit card not found"));
        if (!card.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");

        if (updates.getCardIssuer() != null) card.setCardIssuer(updates.getCardIssuer());
        if (updates.getCardName() != null) card.setCardName(updates.getCardName());
        if (updates.getCardNetwork() != null) card.setCardNetwork(updates.getCardNetwork());
        if (updates.getCardLastFour() != null) card.setCardLastFour(updates.getCardLastFour());
        if (updates.getCreditLimit() != null) card.setCreditLimit(updates.getCreditLimit());
        if (updates.getBillingCycleDay() != null) card.setBillingCycleDay(updates.getBillingCycleDay());
        if (updates.getPaymentDueDay() != null) card.setPaymentDueDay(updates.getPaymentDueDay());
        if (updates.getRewardType() != null) card.setRewardType(updates.getRewardType());
        if (updates.getAnnualFee() != null) card.setAnnualFee(updates.getAnnualFee());
        if (updates.getIsActive() != null) card.setIsActive(updates.getIsActive());
        if (updates.getNotes() != null) card.setNotes(updates.getNotes());
        return creditCardRepository.save(card);
    }

    @Transactional
    public void deleteCreditCard(UUID userId, UUID id) {
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credit card not found"));
        if (!card.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");
        creditCardRepository.delete(card);
    }

    // ── NPS Accounts ──

    @Transactional
    public NpsAccount createNpsAccount(UUID userId, NpsAccount account) {
        account.setUserId(userId);
        return npsAccountRepository.save(account);
    }

    @Transactional
    public NpsAccount updateNpsAccount(UUID userId, UUID id, NpsAccount updates) {
        NpsAccount acc = npsAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NPS account not found"));
        if (!acc.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");

        if (updates.getPranNumber() != null) acc.setPranNumber(updates.getPranNumber());
        if (updates.getFundManager() != null) acc.setFundManager(updates.getFundManager());
        if (updates.getSchemePreference() != null) acc.setSchemePreference(updates.getSchemePreference());
        if (updates.getTier() != null) acc.setTier(updates.getTier());
        if (updates.getAssetClass() != null) acc.setAssetClass(updates.getAssetClass());
        if (updates.getCra() != null) acc.setCra(updates.getCra());
        if (updates.getOpeningDate() != null) acc.setOpeningDate(updates.getOpeningDate());
        if (updates.getEmployerName() != null) acc.setEmployerName(updates.getEmployerName());
        if (updates.getCurrentValue() != null) acc.setCurrentValue(updates.getCurrentValue());
        if (updates.getSchemeCode() != null) acc.setSchemeCode(updates.getSchemeCode());
        if (updates.getUnits() != null) acc.setUnits(updates.getUnits());
        if (updates.getNotes() != null) acc.setNotes(updates.getNotes());
        return npsAccountRepository.save(acc);
    }

    @Transactional
    public void deleteNpsAccount(UUID userId, UUID id) {
        NpsAccount acc = npsAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NPS account not found"));
        if (!acc.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");
        npsAccountRepository.delete(acc);
    }

    // ── PPF Accounts ──

    @Transactional
    public PpfAccount createPpfAccount(UUID userId, PpfAccount account) {
        account.setUserId(userId);
        return ppfAccountRepository.save(account);
    }

    @Transactional
    public PpfAccount updatePpfAccount(UUID userId, UUID id, PpfAccount updates) {
        PpfAccount acc = ppfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PPF account not found"));
        if (!acc.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");

        if (updates.getAccountNumber() != null) acc.setAccountNumber(updates.getAccountNumber());
        if (updates.getBankOrPostOffice() != null) acc.setBankOrPostOffice(updates.getBankOrPostOffice());
        if (updates.getBranch() != null) acc.setBranch(updates.getBranch());
        if (updates.getOpeningDate() != null) acc.setOpeningDate(updates.getOpeningDate());
        if (updates.getMaturityDate() != null) acc.setMaturityDate(updates.getMaturityDate());
        if (updates.getNominee() != null) acc.setNominee(updates.getNominee());
        if (updates.getCurrentBalance() != null) acc.setCurrentBalance(updates.getCurrentBalance());
        if (updates.getCurrentFyDeposit() != null) acc.setCurrentFyDeposit(updates.getCurrentFyDeposit());
        if (updates.getInterestRate() != null) acc.setInterestRate(updates.getInterestRate());
        if (updates.getNotes() != null) acc.setNotes(updates.getNotes());
        return ppfAccountRepository.save(acc);
    }

    @Transactional
    public void deletePpfAccount(UUID userId, UUID id) {
        PpfAccount acc = ppfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PPF account not found"));
        if (!acc.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");
        ppfAccountRepository.delete(acc);
    }

    // ── EPF Accounts ──

    @Transactional
    public EpfAccount createEpfAccount(UUID userId, EpfAccount account) {
        account.setUserId(userId);
        return epfAccountRepository.save(account);
    }

    @Transactional
    public EpfAccount updateEpfAccount(UUID userId, UUID id, EpfAccount updates) {
        EpfAccount acc = epfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EPF account not found"));
        if (!acc.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");

        if (updates.getUanNumber() != null) acc.setUanNumber(updates.getUanNumber());
        if (updates.getPfNumber() != null) acc.setPfNumber(updates.getPfNumber());
        if (updates.getEmployerName() != null) acc.setEmployerName(updates.getEmployerName());
        if (updates.getDateOfJoining() != null) acc.setDateOfJoining(updates.getDateOfJoining());
        if (updates.getEmployeeContributionRate() != null) acc.setEmployeeContributionRate(updates.getEmployeeContributionRate());
        if (updates.getEmployerContributionRate() != null) acc.setEmployerContributionRate(updates.getEmployerContributionRate());
        if (updates.getCurrentBalance() != null) acc.setCurrentBalance(updates.getCurrentBalance());
        if (updates.getBasicSalary() != null) acc.setBasicSalary(updates.getBasicSalary());
        if (updates.getIsActive() != null) acc.setIsActive(updates.getIsActive());
        if (updates.getNotes() != null) acc.setNotes(updates.getNotes());
        return epfAccountRepository.save(acc);
    }

    @Transactional
    public void deleteEpfAccount(UUID userId, UUID id) {
        EpfAccount acc = epfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EPF account not found"));
        if (!acc.getUserId().equals(userId)) throw new IllegalArgumentException("Not authorized");
        epfAccountRepository.delete(acc);
    }
}
