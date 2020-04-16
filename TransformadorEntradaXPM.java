/**
 * 
 */
package co.com.experian.omnia.motorcrts.entrada;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.time.*;
import java.time.format.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import co.com.experian.omnia.motorcrts.core.insumos.Consulta;
import co.com.experian.omnia.motorcrts.core.insumos.Cuenta;
import co.com.experian.omnia.motorcrts.core.insumos.Persona;
import co.com.experian.omnia.motorcrts.core.utilidades.Fechas;
import co.com.experian.omnia.motorcrts.entrada.edf.dto.AdjetivoDTO;
import co.com.experian.omnia.motorcrts.entrada.edf.dto.FrequentBehaviourDTO;
import co.com.experian.omnia.motorcrts.exceptions.AdaptadorEntradaException;

/**
 * @author c12976a
 */
public class TransformadorEntradaXPM implements TransformadorEntrada, Serializable {

	/** Serial UID */
	private static final long serialVersionUID = 2713044853821281621L;
	private static Fechas FECHAS = new Fechas();
	private static Logger logger = LogManager.getLogger(TransformadorEntradaXPM.class);
	private static TransformadorEntradaXPM solitario = new TransformadorEntradaXPM();
	private static Map<Integer, Double> salariosMinimos = new HashMap<Integer, Double>();
	public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
	public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMM");

	/** Constructor por defecto */
	private TransformadorEntradaXPM() {
		super();
	}

	/**
	 * @return TransformadorEntradaXPM
	 */
	public static TransformadorEntradaXPM getTransformadorEntradaXPM(Map<String, String> salarios) {

		for (Entry<String, String> salario : salarios.entrySet()) {
			String llave = salario.getKey();
			String valor = salario.getValue();
			salariosMinimos.put(Integer.valueOf(llave), Double.valueOf(valor));
		}

		return solitario;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * co.com.experian.omnia.motorcrts.entrada.TransformadorEntrada#procesar(java.
	 * lang.String)
	 */
	@Override
	public Persona procesar(String data) throws AdaptadorEntradaException {

		JsonObject jsonObject = new JsonParser().parse(data).getAsJsonObject();
		Persona persona = procesar(jsonObject);
		return persona;
	}

	/**
	 * Metodo encargado de leer la informacion de entrada en formato XPM y
	 * convertirla en un objeto persona
	 * 
	 * @param jsonData
	 * @return @Persona
	 * @throws AdaptadorEntradaException
	 */
	public static Persona procesar(JsonObject jsonData) throws AdaptadorEntradaException {

		Persona persona = new Persona();
		long startTime, endTime;

		try {

			LocalDateTime datetime = LocalDateTime.now();

			/* TODO Instanciacion del objeto persona y set de valor del rundate */
			String fecha = formatter.format(datetime);
			Integer runDate = 201911 , fechaConsulta = 201911;
			// runDate = fechaConsulta = Integer.valueOf(fecha);

			Integer date = datetime.getDayOfMonth();

			/*if (date > 1 && date <= 10)
				runDate = FECHAS.restaMeses(runDate, 2);
			else if (date > 10)
				runDate = FECHAS.restaMeses(runDate, 1);*/

			/* Datos del objeto persona */
			Integer fechaInicial = FECHAS.restaMeses(runDate, 48);
			Integer fechaCalculo = FECHAS.restaMeses(runDate, 0);

			persona.setFecha(runDate);
			persona.setFechaConsulta(fechaConsulta);
			JsonObject identification = null;

			if (jsonData.has("bestIdentifications")) {

				startTime = System.nanoTime();
				
				JsonArray identifications = jsonData.getAsJsonArray("bestIdentifications");
				identification = (JsonObject) identifications.get(0);
				String tipoId = identification.get("personIdType").getAsString();
				persona.setTipoId(tipoId != null && !tipoId.equals("null") ? Integer.valueOf(tipoId) : 0);
				String numId = "0";

				if (identification.has("personIdNumber")) {
					numId = identification.get("personIdNumber").getAsString();
				} else {
					throw new NullPointerException("Persona sin numero de Id.");
				}

				persona.setId(numId != null && !numId.equals("null") ? Long.valueOf(numId) : 0L);
				endTime = System.nanoTime();
				logger.debug(
					"ID:" + persona.getId() + "Datos del objeto persona. Start time: "+ startTime +" ns, End Time: "+ endTime + " ns, Spend Time: " + (endTime - startTime) + "\n"
				);
			}

			if (jsonData.has("inquiryFootprints")){
				
				/* Datos del objeto consultas */
				JsonArray inquiries = jsonData.getAsJsonArray("inquiryFootprints");
	
				if (inquiries != null) {
	
					startTime = System.nanoTime();
					inquiries.forEach(inquiry -> {
	
						JsonObject footPrint = inquiry.getAsJsonObject();
						if (footPrint.has("inquiryDate") && footPrint.has("businessLineCode") && footPrint.has("counterpartyIdNumber")){
							String businessLineCode = footPrint.get("businessLineCode").getAsString();
							String counterpartyIdNumber = footPrint.get("counterpartyIdNumber").getAsString();
		
							if (footPrint.get("inquiryDate") != null){
								if (epochInt(footPrint.get("inquiryDate").getAsLong()) >= FECHAS.restaMeses(fechaConsulta, 6)){
									Consulta consulta = new Consulta();
									consulta.setFecha(epochInt(footPrint.get("inquiryDate").getAsLong()));
									consulta.setCodigoSuscriptor(businessLineCode);
									consulta.setNitSuscriptor(counterpartyIdNumber);
									persona.addConsulta(consulta);
								}
							}
						}
	
					});
					endTime =  System.nanoTime();
					logger.debug(
						"ID:" + persona.getId() + "Datos del objeto Consultas 1.0. Start time: "+ startTime +" ns, End Time: "+ endTime + " ns, Spend Time: " + (endTime - startTime) +
						" ns, No. Inquieries: " + inquiries.size() + "\n"
					);
				}
			}

			JsonArray counterparties = jsonData.getAsJsonArray("counterparties");
			
			startTime = System.nanoTime();
			for (Consulta huellaConsulta : persona.getConsultas()) {

				String businessLineCode = huellaConsulta.getCodigoSuscriptor();
				String counterpartyIdNumber = huellaConsulta.getNitSuscriptor();
				Iterator<JsonElement> iterator = counterparties.iterator(); 
				
				breakLoop:

				while (iterator.hasNext()) {
					
					JsonObject counterparty = iterator.next().getAsJsonObject();
					
					if (counterparty.has("counterpartyIdNumber")){

						if (counterparty.get("counterpartyIdNumber").getAsString().equals(counterpartyIdNumber) && 
								counterparty.has("businessLines")) {
	
							JsonArray businessLines = counterparty.getAsJsonArray("businessLines");
							Iterator<JsonElement> iteratorBL = businessLines.iterator();
	
							while (iteratorBL.hasNext()) {
	
								JsonObject businessLine = iteratorBL.next().getAsJsonObject();
								
								if (businessLine.has("businessLineCode") && businessLine.has("businessLineStatus")) {
									if (businessLine.get("businessLineCode").getAsString().equals(businessLineCode)) {
		
										String businessLineStatus = businessLine.get("businessLineStatus").getAsString();
										huellaConsulta.setEstadoSuscriptor(businessLineStatus);
										break breakLoop;
									}
								}
							}
						}
					}
				}
			}
			endTime = System.nanoTime();
			logger.debug(
				"ID:" + persona.getId() + "Datos del objeto Consultas 1.1. Start time: "+ startTime +" ns, End Time: "+ endTime + " ns, Spend Time: " + (endTime - startTime) +
				" ns, No. Consultas: " + persona.getConsultas().size() + "\n"
			);

			/* Demografico */
			if (identification != null) {
				
				startTime = System.nanoTime();

				if (identification.has("officialSourceValidationStatus") && identification.has("idStatus")) {
					Boolean officialSourceValidationIndicator = identification.get("officialSourceValidationStatus")
							.getAsBoolean();

					if (officialSourceValidationIndicator) {
						String idStatus = identification.get("idStatus").getAsString();
						persona.getDemografico().setEstadoVida(idStatus);
					}
				}

				if (identification.has("issuingCityCodeAsReported") && persona.getTipoId().equals(1)) {
					String issuingCityCode = identification.get("issuingCityCodeAsReported").getAsString();
					persona.getDemografico().setCiudadExpedicionDocumento(issuingCityCode);

					if (identification.has("idStatus") && identification.has("issueDate")) {
						persona.getDemografico().setFechaExpedicionDocumento(epochString(identification.get("issueDate").getAsLong()));
					}
				}
				
				endTime = System.nanoTime();
				logger.debug(
					"ID:" + persona.getId() + "Demografico. Start time: "+ startTime +" ns, End Time: "+ endTime + " ns, Spend Time: " + (endTime - startTime) +
					" ns\n"
				);
			}

			/* Cuentas */
			if (jsonData.has("bestAccounts")) {

				JsonArray accounts = jsonData.getAsJsonArray("bestAccounts");

				startTime = System.nanoTime();
				accounts.forEach(jsonAccount -> {

					JsonObject account = jsonAccount.getAsJsonObject();
					
					if (account.has("businessLineCode") && account.has("accountType") &&
							account.has("accountNumber")) {

						List<FrequentBehaviourDTO> dates = new ArrayList<>();
	
						Integer fechaInicio = fechaCalculo;
						
						long start,end;
						start = System.nanoTime();
						
						if (account.has("frequentBehaviour")) {
							JsonArray frequentBehaviourVector = account.get("frequentBehaviour").getAsJsonArray();
	
							while (fechaInicio > fechaInicial) {					
	
								try {
	
									FrequentBehaviourDTO behaviourPOJO = new FrequentBehaviourDTO();
									
									Date fechaVector = dateFormat.parse(String.valueOf(fechaInicio));
	
									behaviourPOJO.setFrequentBehaviourDate(fechaVector);					
	
									frequentBehaviourVector.forEach(frequentBehaviourElement->{
										
										JsonObject frequentBehaviourObject = frequentBehaviourElement.getAsJsonObject();
										
										if (frequentBehaviourObject.has("behaviourDate")) {
											
											JsonElement behaviourDateElement = frequentBehaviourObject.get("behaviourDate");
	
											if (behaviourDateElement.getAsString() != null){
											
												Date fechaVectorXPM = new Date(behaviourDateElement.getAsLong());								
												
												if (dateFormat.format(fechaVector).equals(dateFormat.format(fechaVectorXPM))) {
													behaviourPOJO.setFrequentBehaviour(frequentBehaviourObject);
													return;
												}
												
											} else {
												behaviourPOJO.setFrequentBehaviour(null);
												logger.info("'behaviourDate' has not a valid date format in 'frequentBehaviour' XPM object");
											}
	
										} else {
											behaviourPOJO.setFrequentBehaviour(null);
											logger.info("'behaviourDate' is a required field in 'frequentBehaviour' XPM object");
										}
									});
	
									dates.add(behaviourPOJO);
									fechaInicio = FECHAS.restaMeses(fechaInicio, 1);
	
								} catch (Exception e) {
									logger.error("!!! error: ", e);
								}						
							}	
						}
	
						Collections.sort(dates);
						JsonObject frequentBehaviour = null;
						
						for(int indice = 0; indice < dates.size(); indice++){
							if (dates.get(indice).getFrequentBehaviour() != null) {
								frequentBehaviour = dates.get(indice).getFrequentBehaviour();
							}
						}
						end = System.nanoTime();
						logger.debug(
								"ID:" + persona.getId() + "Fechas Cuentas. Start time: "+ start +" ns, End Time: "+ end + " ns, Spend Time: " + (end - start) +
								" ns\n"
						);					
						
						start = System.nanoTime();
						
						Cuenta cuenta = new Cuenta();
						
						Integer fechaActualizacion = account.has("cutoffDate") ? epochInt(account.get("cutoffDate").getAsLong()) : null;
						
						cuenta.setFechaActualizacion(fechaActualizacion);
						String codigoSuscriptor = account.get("businessLineCode").getAsString();
						cuenta.setCodigoSuscriptor(codigoSuscriptor);

						String tipoCuenta = account.get("accountType").getAsString();
						cuenta.setTipoCuenta(tipoCuenta);
						String numeroCuenta = account.get("accountNumber").getAsString();
						cuenta.setNumeroCuenta(numeroCuenta);

						if (!cuenta.getTipoCuenta().equals("1") && !cuenta.getTipoCuenta().equals("51")) {
	
							if (account.has("typeOfDebtor") && account.has("typeOfCredit")) {
								cuenta.setGarante(account.get("typeOfDebtor").getAsInt());
								cuenta.setTipoObligacion(account.get("typeOfCredit").getAsInt());
							}
	
							if (account.has("guaranteeType")) {
								String tipoGarantia = account.get("guaranteeType").getAsString();
								cuenta.setTipoGarantia(Double.valueOf(tipoGarantia).intValue());
							}
	
							if (account.has("totalNumberOfInstallments")) {
								String cuotas = account.get("totalNumberOfInstallments").getAsString();
								cuenta.setCuotas(cuotas != "" ? Double.valueOf(cuotas).intValue() : null);
							}
	
							if (account.has("expiryDate")) {
								cuenta.setFechaVencimiento(epochInt(account.get("expiryDate").getAsLong()));
							}
	
							if (frequentBehaviour != null && frequentBehaviour.has("accountStatus")) {
								cuenta.setEstadoObligacion(frequentBehaviour.get("accountStatus").getAsInt());
							}
	
							if (account.has("originStatusOfAccount")) {
								cuenta.setOrigenCredito(account.get("originStatusOfAccount").getAsInt());
							}
	
							if (account.has("periodicityOfPayments")) {
								cuenta.setPeriodicidadPago(account.get("periodicityOfPayments").getAsInt());
							}
	
							if (account.has("paymentType")) {
								cuenta.setFormaPago(account.get("paymentType").getAsInt());
							}
	
							/* Situacion titular */
	
							if (account.has("legal")) {
	
								JsonArray legals = account.get("legal").getAsJsonArray();
	
								legals.forEach(legalObject -> {
	
									JsonObject legal = legalObject.getAsJsonObject();
	
									if (cuenta.getCodigoSuscriptor().equals(legal.get("businessLineCode").getAsString())
											&& cuenta.getTipoCuenta().equals(legal.get("accountType").getAsString())
											&& cuenta.getNumeroCuenta().equals(legal.get("accountNumber").getAsString())) {
										if (legal.has("stateOfAccountHolder")){
											cuenta.setSituacionTitular(legal.get("stateOfAccountHolder").getAsInt());
											return;
										}
									}
								});
							}
						}
	
						if (account.has("accountClosedDate")) {
							String estado = account.get("accountClosedDate").getAsString();
	
							if (estado != null && !estado.contains("-999999999")) {
								cuenta.setEstado("Cerrada");
							} else {
								cuenta.setEstado("Abierta");
							}
	
							if (cuenta.getEstado().equals("Cerrada")){
								cuenta.setFechaCancelacion(epochInt(account.get("accountClosedDate").getAsLong()));								
							}
							cuenta.setFechaActualizacion(epochInt(account.get("accountClosedDate").getAsLong()));
	
						} else {
							cuenta.setEstado("Abierta");
						}
	
						if (account.has("accountOpeningDate")) {
							cuenta.setFechaApertura(epochInt(account.get("accountOpeningDate").getAsLong())); 
						}
	
						if (account.has("counterpartyIdNumber")) {
							String nit = account.get("counterpartyIdNumber").getAsString();
							cuenta.setNIT(nit);
						}
	
						if (frequentBehaviour != null && frequentBehaviour.has("bureauEvent")) {
							String novedad = frequentBehaviour.get("bureauEvent").getAsString();
							cuenta.setNovedad(!novedad.equals("") ? Double.valueOf(novedad).intValue() : null);
						}
	
						if (frequentBehaviour != null && frequentBehaviour.has("rating")) {							
							cuenta.setCalificacion(frequentBehaviour.get("rating").getAsString());
						}
						
						if (account.has("blockSeverity")){
							Integer bloqueo = account.get("blockSeverity").getAsInt();
							if (bloqueo.equals(0)){
								cuenta.setBloqueo(bloqueo);
							} else if(bloqueo.equals(1)){
								if (account.has("outdatedAccountDate")){
									if (!account.get("outdatedAccountDate").getAsString().contains("-999999999")){
										cuenta.setBloqueo(7);
									}
								}								
							}
						}
	
						if (account.has("disputeIndicator")) {
							Boolean reclamo = account.get("disputeIndicator").getAsBoolean();
							cuenta.setReclamo(reclamo ? Integer.valueOf(1) : Integer.valueOf(0));
						}
	
						int index = 0;					
	
						for (FrequentBehaviourDTO pojo : dates) {
	
							Integer dateCounter = FECHAS.sumaMeses(fechaInicial, index);
							Integer dateYear = Integer.valueOf(dateCounter / 100);
							index++;
	
							if (dateCounter <= fechaCalculo) {
	
								frequentBehaviour = pojo.getFrequentBehaviour();
								Double smmlv = 0D;
	
								smmlv = salariosMinimos.get(dateYear);
	
								if (frequentBehaviour != null && frequentBehaviour.has("debtBalance")) {
									String debtBalance = frequentBehaviour.get("debtBalance").getAsString();
									Double saldo = debtBalance != null ? Double.valueOf(debtBalance) : null;
									saldo = obtenerValor(saldo, smmlv, 2);
									cuenta.addVectorSaldo(saldo);
								} else {
									cuenta.addVectorSaldo(null);
								}
	
								if (frequentBehaviour != null && frequentBehaviour.has("valueBalanceOverdue")) {
									String valueBalanceOverdue = frequentBehaviour.get("valueBalanceOverdue").getAsString();
									Double saldoMora = valueBalanceOverdue != null ? Double.valueOf(valueBalanceOverdue)
											: null;
									saldoMora = obtenerValor(saldoMora, smmlv, 2);
									cuenta.addVectorSaldoMora(saldoMora);
								} else {
									cuenta.addVectorSaldoMora(null);
								}
	
								if (frequentBehaviour != null && frequentBehaviour.has("valueMonthlyPayment")) {
									String valueMonthlyPayment = frequentBehaviour.get("valueMonthlyPayment").getAsString();
									Double cuota = valueMonthlyPayment != null ? Double.valueOf(valueMonthlyPayment) : null;
									cuota = obtenerValor(cuota, smmlv, 2);
									cuenta.addVectorCuota(cuota);
								} else {
									cuenta.addVectorCuota(null);
								}
	
								if (frequentBehaviour != null && frequentBehaviour.has("initialValue")) {
									String initialValue = frequentBehaviour.get("initialValue").getAsString();
									Double cupo = initialValue != null ? Double.valueOf(initialValue) : null;
									cupo = obtenerValor(cupo, smmlv, 2);
									cuenta.addVectorCupo(cupo);
								} else {
									cuenta.addVectorCupo(null);
								}
							}
						}
						end = System.nanoTime();
						logger.debug(
								"ID:" + persona.getId() + "Atributos Cuentas. Start time: "+ start +" ns, End Time: "+ end + " ns, Spend Time: " + (end - start) +
								" ns\n"
						);
	
						/* Vector de comportamiento */
						
						start = System.nanoTime();
	
						if (account.has("normalizedBehaviourVectorDA")) {
	
							String businessBehaviourVectorNormalized = account.get("normalizedBehaviourVectorDA")
									.getAsString();
	
							char[] vect = businessBehaviourVectorNormalized.toCharArray();
							index = 0;
	
							for (char a : vect) {
	
								Integer dateCounter = FECHAS.sumaMeses(fechaInicial, index);
								index++;
	
								if (dateCounter <= fechaCalculo) {
	
									if (a == ' ' || a == '.') {
										cuenta.addVectorMorosidad(null);
									} else {
										cuenta.addVectorMorosidad((double) Character.getNumericValue(a));
									}
								}
							}
						}
						end = System.nanoTime();
						logger.debug(
								"ID:" + persona.getId() + "Vector Comportamiento. Start time: "+ start +" ns, End Time: "+ end + " ns, Spend Time: " + (end - start) +
								" ns\n"
						);
	
						/* Adjetivos */
						start = System.nanoTime();
						
						ArrayList<AdjetivoDTO> adjetivosCuenta = new ArrayList<>();
						AdjetivoDTO adjetivo1 = new AdjetivoDTO(), adjetivo2 = new AdjetivoDTO();
	
						if (account.has("deceasedDate")) {
							String deceasedDate = account.get("deceasedDate").getAsString();
	
							if (!deceasedDate.contains("-999999999")) {
	
								adjetivo1.setFecha(new Date(Long.valueOf(deceasedDate)));
								adjetivo1.setAdjetivo("1");
	
							}
						}
	
						if (account.has("permanentDisabilityDate")) {
							String permanentDisabilityDate = account.get("permanentDisabilityDate").getAsString();
	
							if (!permanentDisabilityDate.contains("-999999999")) {
	
								adjetivo2.setFecha(new Date(Long.valueOf(permanentDisabilityDate)));
								adjetivo2.setAdjetivo("5");
	
							}
						}
	
						if (account.has("debtorNotFoundDate")) {
							String debtorNotFoundDate = account.get("debtorNotFoundDate").getAsString();
	
							if (!debtorNotFoundDate.contains("-999999999")) {
	
								AdjetivoDTO adjetivoDTO = new AdjetivoDTO();
								adjetivoDTO.setFecha(new Date(Long.valueOf(debtorNotFoundDate)));
								adjetivoDTO.setAdjetivo("3");
								adjetivosCuenta.add(adjetivoDTO);
	
							}
						}
	
						if (account.has("accountAtCollectionDate")) {
							String accountAtCollectionDate = account.get("accountAtCollectionDate").getAsString();
	
							if (!accountAtCollectionDate.contains("-999999999")) {
	
								AdjetivoDTO adjetivoDTO = new AdjetivoDTO();
								adjetivoDTO.setFecha(new Date(Long.valueOf(accountAtCollectionDate)));
								adjetivoDTO.setAdjetivo("2");
								adjetivosCuenta.add(adjetivoDTO);
	
							}
						}
	
						if (account.has("lineDisconnectedDate")) {
							String lineDisconnectedDate = account.get("lineDisconnectedDate").getAsString();
	
							if (!lineDisconnectedDate.contains("-999999999")) {
	
								AdjetivoDTO adjetivoDTO = new AdjetivoDTO();
								adjetivoDTO.setFecha(new Date(Long.valueOf(lineDisconnectedDate)));
								adjetivoDTO.setAdjetivo("4");
								adjetivosCuenta.add(adjetivoDTO);
	
							}
						}
	
						if (account.has("prelegalCollectionDate")) {
							String prelegalCollectionDate = account.get("prelegalCollectionDate").getAsString();
	
							if (!prelegalCollectionDate.contains("-999999999")) {
	
								AdjetivoDTO adjetivoDTO = new AdjetivoDTO();
								adjetivoDTO.setFecha(new Date(Long.valueOf(prelegalCollectionDate)));
								adjetivoDTO.setAdjetivo("6");
								adjetivosCuenta.add(adjetivoDTO);
	
							}
						}
	
						if (account.has("legalCollectionDate")) {
							String legalCollectionDate = account.get("legalCollectionDate").getAsString();
	
							if (!legalCollectionDate.contains("-999999999")) {
	
								AdjetivoDTO adjetivoDTO = new AdjetivoDTO();
								adjetivoDTO.setFecha(new Date(Long.valueOf(legalCollectionDate)));
								adjetivoDTO.setAdjetivo("7");
								adjetivosCuenta.add(adjetivoDTO);
	
							}
						}
	
						Collections.sort(adjetivosCuenta);
	
						if (adjetivo2.getAdjetivo() != null) {
							adjetivosCuenta.add(adjetivo2);
						}
	
						if (adjetivo1.getAdjetivo() != null) {
							adjetivosCuenta.add(adjetivo1);
						}
	
						Collections.reverse(adjetivosCuenta);
						
						adjetivosCuenta.forEach(adjetivo->{
							if (cuenta.getAdjetivo1() == null) {
								cuenta.setAdjetivo1(adjetivo.getAdjetivo());
								cuenta.setFechaAdjetivo1(Double.valueOf(dateFormat.format(adjetivo.getFecha())).intValue());
							}
	
							else if (cuenta.getAdjetivo2() == null) {
								cuenta.setAdjetivo2(adjetivo.getAdjetivo());
								cuenta.setFechaAdjetivo2(Double.valueOf(dateFormat.format(adjetivo.getFecha())).intValue());
							}
	
							else if (cuenta.getAdjetivo3() == null) {
								cuenta.setAdjetivo3(adjetivo.getAdjetivo());
								cuenta.setFechaAdjetivo3(Double.valueOf(dateFormat.format(adjetivo.getFecha())).intValue());
							}
						});					
						
						end = System.nanoTime();
						logger.debug(
								"ID:" + persona.getId() + "Adjetivos Cuentas. Start time: "+ start +" ns, End Time: "+ end + " ns, Spend Time: " + (end - start) +
								" ns\n"
						);
						
						if (counterparties != null){
							if (account.has("counterpartyIdNumber") && account.has("businessLineCode")) {
								String counterpartyIdNumber = account.get("counterpartyIdNumber").getAsString();
								String businessLineCode = account.get("businessLineCode").getAsString();
								Iterator<JsonElement> iterator = counterparties.iterator();
			
								start = System.nanoTime();
								
								breakLoop:
			
								/* Estado del suscriptor en la cuenta */
								while (iterator.hasNext()) {
			
									JsonObject counterparty = iterator.next().getAsJsonObject();
									
									if (counterparty.has("counterpartyIdNumber")){
			
										if (counterparty.get("counterpartyIdNumber").getAsString().equals(counterpartyIdNumber)) {
				
											JsonArray businessLines = counterparty.getAsJsonArray("businessLines");
											Iterator<JsonElement> iteratorBL = businessLines.iterator();
				
											while (iteratorBL.hasNext()) {
				
												JsonObject businessLine = iteratorBL.next().getAsJsonObject();
				
												if (businessLine.get("businessLineCode").getAsString().equals(businessLineCode)) {
													
													if (businessLine.has("businessLineStatus")) {
				
														String businessLineStatus = businessLine.get("businessLineStatus").getAsString();
														cuenta.setEstadoSuscriptor(businessLineStatus);
														break breakLoop;
				
													}
												}
											}
										}
									}
								}
							}
						}
						end = System.nanoTime();
						logger.debug(
								"ID:" + persona.getId() + "Estado Suscriptor. Start time: "+ start +" ns, End Time: "+ end + " ns, Spend Time: " + (end - start) +
								" ns\n"
						);
						
						boolean bs = cuenta.getEstadoSuscriptor() != null ? true : false;
						boolean br = cuenta.getReclamo().equals(0) ? true : false;
						boolean bb = cuenta.getBloqueo() != null ? true : false;
						boolean ba = true; //(cuenta.getNovedad() != null || cuenta.getTipoCuenta().equals("1") || cuenta.getTipoCuenta().equals("51")) ? true : false;
						boolean bn = cuenta.getFechaActualizacion() != null ? true : false;
						boolean bt = cuenta.getCodigoSuscriptor() != null ? true : false;
						boolean bc = cuenta.getTipoCuenta() != null ? true : false;
						boolean bf = cuenta.getNumeroCuenta() != null ? true : false;
						boolean bp = cuenta.getFechaApertura() != null ? true : false;
						boolean bm = (cuenta.getVectorMorosidad() != null || cuenta.getTipoCuenta().equals("1") || cuenta.getTipoCuenta().equals("51")) ? true : false;
						boolean bv = (cuenta.getFechaVencimiento() != null || cuenta.getTipoCuenta().equals("1") || cuenta.getTipoCuenta().equals("51")) ? true: false;
						
						/* Conjunto de Reglas para Agregar Cuentas
						 * 
						 * Vease: https://confluenceglobal.experian.local/confluence/display/CCBR/XPM+Fields+to+Attributes+Engine
						 */
						if (bs && br && bb && ba && bn && bt && bc && bf && bp && bm && bv){
							if (cuenta.getEstadoSuscriptor().equals("1") && cuenta.getReclamo().equals(0)){
								if (cuenta.getBloqueo().equals(0) || cuenta.getBloqueo().equals(7)){
									persona.addCuenta(cuenta);
								}
							}
						}
					}
				});
				endTime = System.nanoTime();
				logger.debug(
					"ID:" + persona.getId() + "Cuentas. Start time: "+ startTime +" ns, End Time: "+ endTime + " ns, Spend Time: " + (endTime - startTime) +
					" ns\n"
				);
			}

			return persona;

		} catch (Exception e) {
			throw new AdaptadorEntradaException(e.getMessage(), e, persona);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * co.com.experian.omnia.motorcrts.entrada.TransformadorEntrada#procesar(java.
	 * lang.String[])
	 */
	@Override
	public Persona procesar(String[] data) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see co.com.experian.omnia.motorcrts.entrada.TransformadorEntrada#setLog()
	 */
	@Override
	public void setLog() {

	}

	/**
	 * Metodo encargado de redondear a n decimales el valor Double de entrada
	 * 
	 * @param valor
	 * @return Double
	 */
	private static Double obtenerValor(Double valor, Double smmlv, Integer redondeo) {

		if (valor != null && smmlv != 0 && smmlv != null) {
			valor = valor / smmlv;
			return BigDecimal.valueOf(valor).setScale(redondeo, RoundingMode.HALF_UP).doubleValue();
		}

		return null;
	}
	
    public static Date asDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }
    
    public static int epochInt(long date){
    	LocalDateTime datex = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC);
		String fechaset =  formatter.format(datex);
		return Integer.valueOf(fechaset); 
    }
    
    public static String epochString(long date){
    	LocalDateTime datex = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC);
		return formatter.format(datex);
    }
  
}