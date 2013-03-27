package legacy.hedge;

import legacy.dto.Amount;
import legacy.dto.Book;
import legacy.dto.Transaction;
import legacy.error.CheckResult;
import legacy.service.*;
import org.fest.assertions.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.Date;

import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Created with IntelliJ IDEA.
 * User: david
 * Date: 27/03/13
 * Time: 13:39
 * To change this template use File | Settings | File Templates.
 */
@RunWith(MockitoJUnitRunner.class)
public class HedgingPositionManagementImplTest {


    @Spy
    private HedgingPositionManagementImpl service = new HedgingPositionManagementImpl();

    @Mock(answer = Answers.RETURNS_DEFAULTS)
    private IHedgingPositionDataAccessService hedgingPositionDataAccessService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ITradingDataAccessService iTradingDataAccessService;

    @Mock
    private ITransactionManagerService transactionManagerService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IAnalyticalService analyticalService;

    @Before
    public void setUp() throws Exception {
        doReturn(hedgingPositionDataAccessService).when(service).getHedingPositionDataAccessService();
        doReturn(iTradingDataAccessService).when(service).getTradingDateAccessService();
        doReturn(transactionManagerService).when(service).getTransactionManagerService();
        doReturn(analyticalService).when(service).getDataAccessService();

        doReturn(createTradingOrderWithAmount(123)).when(hedgingPositionDataAccessService).getTrade(0);

        doReturn(createCheckResult()).when(service).hedgingPositionMgt(Mockito.any(HedgingPosition.class));

        Mockito.doReturn("TRANSACTION_ID").when(hedgingPositionDataAccessService).getHedgingTransactionIdByTransactionId(Mockito.anyInt());

    }

    private Transaction createTransactionWithWay(final TransactionWay way) {
        Transaction transaction = new Transaction();
        transaction.setWay(way);
        transaction.setBookName("bookname");
        return transaction;
    }

    private CheckResult<HedgingPosition> createCheckResult() {
        CheckResult<HedgingPosition> result = new CheckResult<>();
        result.setResult(new HedgingPosition());
        return result;
    }

    private TradingOrder createTradingOrderWithAmount(final int price) {
        TradingOrder order = new TradingOrder();
        Amount amount = new Amount();
        amount.setPrice(price);
        order.setPrice(amount);
        return order;
    }

    @Test
    public void should_run_code_with_long_transaction() {
        doReturn(createTransactionWithWay(TransactionWay.LONG)).when(iTradingDataAccessService).getTransactionById(0);
        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(new HedgingPosition());
        HedgingPosition value = getHedgingPositionForThisTest(service);
        Assertions.assertThat(value.getTransactionWay()).isEqualTo("L");
    }

    @Test
    public void should_run_code_with_short_transaction() {
        doReturn(createTransactionWithWay(TransactionWay.SHORT)).when(iTradingDataAccessService).getTransactionById(0);
        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(new HedgingPosition());
        HedgingPosition value = getHedgingPositionForThisTest(service);
        Assertions.assertThat(value.getTransactionWay()).isEqualTo("S");
    }

    @Test
    public void should_run_code_with_forbiden_stock() {
        doReturn(createTransactionWithWay(TransactionWay.SHORT)).when(iTradingDataAccessService).getTransactionById(0);
        Mockito.doReturn(null).when(analyticalService).getRetrieveStockByActiveGK(Mockito.anyInt(), Mockito.anyString());
        Mockito.doReturn(createBook()).when(analyticalService).getBookByName("bookname-instock");

        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(new HedgingPosition());

        HedgingPosition value = getHedgingPositionForThisTest(service);
        assertThat(value.getCodtyptra()).isEqualTo(new BigInteger("666"));

    }

    @Test
    public void should_get_transaction_value_when_hp_throw_exception() {

        Transaction transaction = createTransactionWithWay(TransactionWay.LONG);
        transaction.setValueDate(new Date());

        doReturn(transaction).when(iTradingDataAccessService).getTransactionById(0);

        HedgingPosition hp = Mockito.mock(HedgingPosition.class);
        doThrow(new NullPointerException()).when(hp).getValueDate();
        Mockito.doReturn(HedgingPositionTypeConst.INI).when(hp).getType();

        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(hp);

        HedgingPosition position = getHedgingPositionForThisTest(service);

        Mockito.verify(hp, times(2)).setValueDate(transaction.getValueDate());

    }

    @Test
    public void should_run_the_code_with_cancel_position() {
        doReturn(createTransactionWithWay(TransactionWay.SHORT)).when(iTradingDataAccessService).getTransactionById(0);
        HedgingPosition hp = new HedgingPosition();
        hp.setType(HedgingPositionTypeConst.CANCEL_POSITION);

        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(hp);
        HedgingPosition value = getHedgingPositionForThisTest(service);
        Assertions.assertThat(value.getCodetyptkt()).isEqualTo(20);
        Assertions.assertThat(value.getHedgingTransactionId()).isEqualTo("TRANSACTION_ID");


    }

    @Test
    public void should_run_the_code_with_extension() {
        doReturn(createTransactionWithWay(TransactionWay.SHORT)).when(iTradingDataAccessService).getTransactionById(0);
        HedgingPosition hp = new HedgingPosition();
        hp.setType(HedgingPositionTypeConst.EXT);

        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(hp);
        HedgingPosition value = getHedgingPositionForThisTest(service);
        Assertions.assertThat(value.getCodetyptkt()).isEqualTo(42);
        Assertions.assertThat(value.getCombck()).isNull();
        Assertions.assertThat(value.getHedgingTransactionId()).isNull();


    }

    @Test
    public void should_run_the_code_with_extension_and_different_date() {
        Transaction transaction = createTransactionWithWay(TransactionWay.SHORT);
        transaction.setIssueDate(new Date());
        doReturn(transaction).when(iTradingDataAccessService).getTransactionById(0);
        HedgingPosition hp = new HedgingPosition();
        hp.setType(HedgingPositionTypeConst.EXT);

        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(hp);
        HedgingPosition value = getHedgingPositionForThisTest(service);
        Assertions.assertThat(value.getCodetyptkt()).isEqualTo(42);
        Assertions.assertThat(value.getCombck()).isNotNull();
        Assertions.assertThat(value.getHedgingTransactionId()).isNull();

    }


    @Test
    public void should_run_the_code_with_ini() {
        doReturn(createTransactionWithWay(TransactionWay.SHORT)).when(iTradingDataAccessService).getTransactionById(0);
        HedgingPosition hp = new HedgingPosition();
        hp.setType(HedgingPositionTypeConst.INI);

        CheckResult<HedgingPosition> result = service.initAndSendHedgingPosition(hp);
        HedgingPosition value = getHedgingPositionForThisTest(service);
        Assertions.assertThat(value.getCodetyptkt()).isEqualTo(20);
        Assertions.assertThat(value.getHedgingTransactionId()).isNull();
    }

    private HedgingPosition getHedgingPositionForThisTest(final HedgingPositionManagementImpl serviceUnderTest) {
        ArgumentCaptor<HedgingPosition> argument = ArgumentCaptor.forClass(HedgingPosition.class);
        verify(serviceUnderTest).hedgePositionBySendTo3rdParty(argument.capture());
        return argument.getValue();
    }

    private Book createBook() {
        Book book = new Book("fake", 123);
        book.setPortfolioIdByRank("666");
        return book;
    }

}
