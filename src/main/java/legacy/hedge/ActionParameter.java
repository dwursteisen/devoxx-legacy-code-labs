package legacy.hedge;

import legacy.dto.Transaction;
import legacy.service.ITradingDataAccessService;

public class ActionParameter {
    private final HedgingPosition hp;
    private final ITradingDataAccessService trading;
    private final Transaction transaction;

    public ActionParameter(final HedgingPosition hp, final ITradingDataAccessService trading, final Transaction transaction) {
        this.hp = hp;
        this.trading = trading;
        this.transaction = transaction;
    }

    public HedgingPosition getHp() {
        return hp;
    }

    public ITradingDataAccessService getTrading() {
        return trading;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
