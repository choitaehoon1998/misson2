package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.account.type.TransactionResultType.F;
import static com.example.account.type.TransactionResultType.S;
import static com.example.account.type.TransactionType.CANCEL;
import static com.example.account.type.TransactionType.USE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
        @Mock
        private TransactionRepository transactionRepository;

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private AccountUserRepository accountUserRepository;

        @InjectMocks
        private TransactionService transactionService;

        @Test
        void successUseBalance(){
            AccountUser user = AccountUser.builder()
                    .id(12L)
                    .name("Pobi")
                    .build();
            Account account = Account.builder()
                    .accountNumber("1000000012")
                    .accountStatus(AccountStatus.IN_USE)
                    .accountUser(user)
                    .balance(10000L)
                    .build();
            given(accountUserRepository.findById(anyLong()))
                    .willReturn(Optional.of(user));
            given(accountRepository.findByAccountNumber(anyString()))
                    .willReturn(Optional.of(account));
            given(transactionRepository.save(any()))
                    .willReturn(Transaction.builder()
                            .account(account)
                            .transactionType(USE)
                            .transactionResultType(S)
                            .transactionID("transactionId")
                            .transactedAt(LocalDateTime.now())
                            .amount(1000L)
                            .balanceSnapshot(9000L)
                            .build());
            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

            TransactionDto transactionDto = transactionService.useBalance(1L,"1000000000",200L);

            verify(transactionRepository,times(1)).save(captor.capture());

            assertEquals(USE,transactionDto.getTransactionType());
            assertEquals(200L,captor.getValue().getAmount());
            assertEquals(9800L,captor.getValue().getBalanceSnapshot());
            assertEquals(S,transactionDto.getTransactionResultType());
            assertEquals(9000L, transactionDto.getBalanceSnapshot());
            assertEquals(1000L,transactionDto.getAmount());
        }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailedUseTransaction(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionID("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        transactionService.saveFailedTransaction("1000000000",200L);

        verify(transactionRepository,times(1)).save(captor.capture());
        assertEquals(200L,captor.getValue().getAmount());
        assertEquals(10000L,captor.getValue().getBalanceSnapshot());
        assertEquals(F,captor.getValue().getTransactionResultType());
    }


    @Test
    @DisplayName("해당 유저 없음 - 잔액 생성 실패")
    void useBalance_UserNotFound(){
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());
        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L,"1000000000",1000L));
        assertEquals(ErrorCode.USER_NOT_FOUND , exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void deleteAccount_AccountNotFound(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L,"1000000000",1000L));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND , exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름 - 잔액 사용 실")
    void deleteAccountFailed_userUnMatch(){
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        AccountUser hary = AccountUser.builder()
                .id(13L)
                .name("hary")
                .build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(hary)
                        .balance(0L)
                        .accountNumber("1000000012")
                        .build()));
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L,"1000000000",1000L));

        assertEquals(ErrorCode.USER_ACCOUNT_NOT_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("해지 계좌는 사용할 수 없다")
    void deleteAccountFailed_alreadyUnregistered(){
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(pobi)
                        .accountStatus(AccountStatus.UNREGISTERED)
                        .balance(0L)
                        .accountNumber("1000000012")
                        .build()));
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L,"1000000000",1000L));

        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액이 잔액보다 클경우")
    void exceedBalance(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(100L)
                .build();
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L,"1000000000",1000L));

        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE, exception.getErrorCode());

        verify(transactionRepository,times(0)).save(any());
    }

    @Test
    void successCancelBalance(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionID("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();
        given(transactionRepository.findByTransactionID(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(CANCEL)
                        .transactionResultType(S)
                        .transactionID("transactionIdForCancel")
                        .transactedAt(LocalDateTime.now())
                        .amount(200L)
                        .balanceSnapshot(10000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        TransactionDto transactionDto = transactionService.cancelBalance("transactionalId","1000000000",200L);

        verify(transactionRepository,times(1)).save(captor.capture());

        assertEquals(200L,captor.getValue().getAmount());
        assertEquals(10200L,captor.getValue().getBalanceSnapshot());
        assertEquals(S,transactionDto.getTransactionResultType());
        assertEquals(CANCEL,transactionDto.getTransactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapshot());
        assertEquals(200L,transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_AccountNotFound(){
            given(transactionRepository.findByTransactionID(anyString()))
                    .willReturn(Optional.of(Transaction.builder().build()));
            given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId","1000000000",1000L));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND , exception.getErrorCode());
    }

    @Test
    @DisplayName("원 사용 거래 없음 -  잔액 사용 취소 실패")
    void cancelTransaction_TransactionNotFound(){

        given(transactionRepository.findByTransactionID(anyString()))
                .willReturn(Optional.empty());

        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId","1000000000",1000L));

        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND , exception.getErrorCode());
    }

    @Test
    @DisplayName("거래와 계좌가 매칭되지 않음 - 잔액 사용 취소 실패")
    void cancelTransaction_TransactionAccountUnMatch(){

        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();
        Account accountNotUse = Account.builder()
                .id(2L)
                .accountNumber("1000000013")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionID("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionID(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(accountNotUse));

        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId","1000000000",200L));
        assertEquals(ErrorCode.TRANSACTION_ACCOUNT_NOT_MATCH , exception.getErrorCode());
    }

    @Test
    @DisplayName("거래금액과 취소 금액이 다름 - 잔액 사용 취소 실패")
    void cancelTransaction_cancelMustBeFully(){

        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();


        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionID("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionID(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId","1000000000",200L));
        assertEquals(ErrorCode.CANCEL_MUST_FULLY , exception.getErrorCode());
    }


    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelTransaction_tooOldToCancel(){

        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();


        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionID("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionID(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException exception =assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId","1000000000",200L));
        assertEquals(ErrorCode.TOO_OLD_ORDER_TOO_CANCEL, exception.getErrorCode());
    }

    @Test
    void successQueryTransaction(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.IN_USE)
                .accountUser(user)
                .balance(10000L)
                .build();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionID("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .amount(200L)
                .balanceSnapshot(9000L)
                .build();
            given(transactionRepository.findByTransactionID(anyString()))
                    .willReturn(Optional.of(transaction));

            TransactionDto transactionDto = transactionService.queryTransaction("trxId");
            assertEquals(USE, transactionDto.getTransactionType());
            assertEquals(S, transactionDto.getTransactionResultType());
            assertEquals(200L, transactionDto.getAmount());
            assertEquals("transactionId", transactionDto.getTransactionID());
    }
    @Test
    @DisplayName("원거래 없음 - 거래 조회 실패")
    void queryTransaction_TransactionNotFound(){
            given(transactionRepository.findByTransactionID(anyString()))
                    .willReturn(Optional.empty());

            AccountException exception = assertThrows(AccountException.class,
                    () -> transactionService.queryTransaction("transactionId"));

            assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }
}