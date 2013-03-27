package legacy.hedge;

import com.google.common.collect.FluentIterable;
import legacy.DateTimeUtils;
import legacy.dto.Modif;
import legacy.security.User;
import legacy.error.ARPSystemException;
import legacy.error.CheckResult;
import legacy.dto.Book;
import legacy.security.UserSessionsManager;
import legacy.service.*;
import legacy.dto.Transaction;
import legacy.persistence.StorageActionEnum;
import org.apache.commons.lang3.SerializationUtils;

import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HedgingPositionManagementImpl implements IHedgingPositionManagement {

	private static int MAX_DECIMALS = 4;
	private static Logger LOGGER = Logger.getLogger(HedgingPositionManagementImpl.class.getName());


	private ITransactionManagerService transactionManagerService = getTransactionManagerService();

    public ITransactionManagerService getTransactionManagerService() {
        return DataAccessService.getTransactionManagerService();
    }

    @Override
	public CheckResult<HedgingPosition> initAndSendHedgingPosition(HedgingPosition hp) throws ARPSystemException {
		CheckResult<HedgingPosition> result = new CheckResult<HedgingPosition>();
		try {
			hp = initHedgingPosition(hp);
		} catch (Exception e) {
			String errorMsg = "TECHNICAL ERROR, cannot initialize HP to send";
			LOGGER.log(Level.SEVERE, errorMsg, e);
			String msg = hp.getErrorLevel().createHMsgFromError();
			hp.setHedgeMsg(msg);
			result.setCheckIsOk(false);
			try {
				updateHedgingPosition(hp);
			} catch (ARPSystemException e1) {
				LOGGER.log(Level.SEVERE, errorMsg, e1);
			}
			return result;
		}
		try {
			result = hedgePositionBySendTo3rdParty(hp);
			if(result.isCheckIsOk()) {
				hp = result.getResult();
				hp.setStatus(HedgingPositionStatusConst.HEDGED);
				updateHedgingPosition(hp);
			} else {
				hp = result.getResult();
				switch(hp.getErrorLevel()){
					case FUNCTIONAL_ERROR:{
						hp.setStatus(HedgingPositionStatusConst.REJECTED);
						break;
					}
					case CONNECT_ERROR: {
						hp.setStatus(HedgingPositionStatusConst.REJECTED);
						break;
					}
					case BOOKING_MALFUNCTION: {
						//TO DO
						break;
					}
					default: {
						break;
					}
				}
				updateHedgingPosition(hp);
			}
		} catch(ARPSystemException e) {
			LOGGER.log(Level.SEVERE,e.getMessage(), e);
		}
		return result;
	}

	public CheckResult<HedgingPosition> hedgePositionBySendTo3rdParty(HedgingPosition hp) {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.log(Level.FINEST,"Begin 3r party processing. stand by");
		}
		CheckResult<HedgingPosition> result;
		result = hedgingPositionMgt(hp);
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.log(Level.FINEST,"3r party processing is now finished, thank you for your patience"); // t'es con michel
		}
		return result;
	}

    public CheckResult<HedgingPosition> hedgingPositionMgt(final HedgingPosition hp) {
        return HedgingPositionMgt.hedgingPositionMgt(hp);
    }

    private HedgingPosition updateHedgingPosition(HedgingPosition hp) {
		HedgingPosition hpUpdate = SerializationUtils.clone(hp);
		try {
			if (hp.getType().equals(HedgingPositionTypeConst.INI)) {
				hpUpdate.setTransactionId(hp.getTransactionId());
				Modif modif = new Modif();
				modif.setCreDate(new Date());
				hp.setLastModification(modif);
				hp.setStorageUpdate(StorageActionEnum.CREATE);
				hpUpdate = transactionManagerService.classStorageAction(hp);
			} else {
				hp.setStorageUpdate(StorageActionEnum.UPDATE);
				hpUpdate = transactionManagerService.classStorageAction(hp);
			}
		} catch(Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(),e);
			throw e;
		}
		return hpUpdate;
	}

	public HedgingPosition initHedgingPosition(HedgingPosition hp) {
		ITradingDataAccessService trading = getTradingDateAccessService();
        Transaction transaction = trading.getTransactionById(hp.getId());
		long dId = trading.getOptionalIdFromTransaction(transaction);

		double price = getHedingPositionDataAccessService().getPriceQuote(dId, transaction);
		long dps = trading.computeDPSOnTheGrid(transaction.getOuterEdge());
		String combck = dId + " " + transaction.getId() + " CONTROL: [" + getHedingPositionDataAccessService().getControl() + "]";

        Date valueDate = findTransactionDate(hp, transaction);
		String userIni = getUser();
		hp.setIkRtH(userIni);
		switch (hp.getType()) {
			case INI: {
                String transactionWay = new TransactionWrapper(transaction).getWay();

				Integer stock = getDataAccessService().getRetrieveStockByActiveGK(transaction.getId(), transactionWay);
				TradingOrder evt = getHedingPositionDataAccessService().getTrade(transaction.getId());

                int bodCode = computeBodCode(transaction, stock);
                /*********************************** INPUT DEAL DATA *********************/
				hp.setTransactionWay(transactionWay);
				hp.setCodetyptkt(34);
				hp.setCodtyptra(BigInteger.valueOf(bodCode));
				hp.setQuantity(String.valueOf(evt.getPrice().getQuantity()));
				hp.setBasprx(evt.getPrice().getFxPrice() / 100);
				hp.setPrxref(evt.getPrice().getFxPrice());
				hp.setCombck(combck);
				/*********************************** INPUT EVENT DATA *********************/
				hp.setTransactionId(transaction.getId());
				hp.setValueDate(valueDate);
                /*********************************** INPUT DEAL DATA *********************/
                hp.setCodetyptkt(20);
                /*********************************** INPUT EVENT DATA *********************/
                hp.setValueDate(valueDate);
                break;
            }
			case CANCEL_TRANSACTION:
				/*********************************** INPUT DEAL DATA *********************/
				hp.setCodetyptkt(20);
				/*********************************** INPUT EVENT DATA *********************/
				hp.setValueDate(valueDate);
				break;
			case EXT:
				TradingOrder evt = getHedingPositionDataAccessService().getTrade(transaction.getId());
				double fxprice = -1d;
				if (evt !=null ){
					price = evt.getPrice().getPrice();
					fxprice = evt.getPrice().getFxPrice();
				}
				if (price > 0) {
					price = price * fxprice;
				}
				/*********************************** INPUT DEAL DATA *********************/
				hp.setBasprx(price / 100);
				hp.setPrxref(price);
				hp.setCodetyptkt(42);
				hp.setQuantity(String.valueOf(evt.getPrice().getQuantity()));
				/*********************************** INPUT EVENT DATA *********************/
				Date issueDate = transaction.getIssueDate();
				Date tradeDate = transaction.getTradeDate();
				if (DateTimeUtils.compareDate(issueDate,tradeDate)) {
					hp.setCreDate(issueDate);
					hp.setDaprx(tradeDate);
					hp.setDatefinthe(valueDate);
				} else {
					hp.setCreDate(issueDate);
					hp.setDaprx(tradeDate);
					hp.setDatefinthe(tradeDate);
					hp.setCombck(combck);
				}
				hp.setValueDate(valueDate);
				break;
			case CANCEL_POSITION:
				/*********************************** INPUT DEAL DATA *********************/
				hp.setCodetyptkt(20);
				hp.setHedgingTransactionId(getHedingPositionDataAccessService().getHedgingTransactionIdByTransactionId(transaction.getId()));
				/*********************************** INPUT EVENT DATA *********************/
				hp.setValueDate(valueDate);
				break;
		}

		return hp;
 	}

    private Date findTransactionDate(final HedgingPosition hp, final Transaction transaction) {
        Date valueDate = new Date();
        try {
            valueDate = hp.getValueDate();
        } catch(Exception e) {
            valueDate = transaction.getValueDate();
        }
        return valueDate;
    }

    int computeBodCode(final Transaction transaction, final Integer stock) {
        int bodCode;
        if (stock == null) {
            Book book = getDataAccessService().getBookByName(transaction.getBookName() + "-instock");
            bodCode = Integer.parseInt(book.getPortfolioIdFromRank());
        } else {
            Book book = getDataAccessService().getBookByName(transaction.getBookName());
            bodCode = book.getCode();
        }
        return bodCode;
    }

    public IAnalyticalService getDataAccessService() {
        return DataAccessService.getAnalyticalService();
    }

    public IHedgingPositionDataAccessService getHedingPositionDataAccessService() {
        return DataAccessService.getHedgingPositionDataAccessService();
    }

    public ITradingDataAccessService getTradingDateAccessService() {
        return DataAccessService.getTradingDataAccessService();
    }

    private String getUser() {
		User user = UserSessionsManager.getInstance().getCurrentUser();
		if (user !=null) {
			return user.getName();
		} else {
			return "autobot";
		}
	}


    public static class TransactionWrapper {
        private final Transaction transaction;

        public TransactionWrapper(final Transaction transaction) {
            this.transaction = transaction;
        }

        private Map<TransactionWay, String> transcodeTable = new HashMap<>();
        {
            transcodeTable.put(TransactionWay.LONG, "L");
            transcodeTable.put(TransactionWay.SHORT, "S");
        }

        public String getWay() {
            String way = transcodeTable.get(transaction.getWay());
            if(way == null) {
              return new String();
            }
            return way;
        }
    }
}
