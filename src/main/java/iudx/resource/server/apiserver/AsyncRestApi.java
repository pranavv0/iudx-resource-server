package iudx.resource.server.apiserver;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import iudx.resource.server.apiserver.handlers.AuthHandler;
import iudx.resource.server.apiserver.handlers.FailureHandler;
import iudx.resource.server.apiserver.handlers.ValidationHandler;
import iudx.resource.server.apiserver.response.ResponseType;
import iudx.resource.server.apiserver.util.RequestType;
import iudx.resource.server.common.Api;
import iudx.resource.server.common.HttpStatusCode;
import iudx.resource.server.common.ResponseUrn;
import iudx.resource.server.database.postgres.PostgresService;
import iudx.resource.server.metering.MeteringService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static iudx.resource.server.apiserver.response.ResponseUtil.generateResponse;
import static iudx.resource.server.apiserver.util.Constants.CONTENT_TYPE;
import static iudx.resource.server.apiserver.util.Constants.APPLICATION_JSON;
import static iudx.resource.server.apiserver.util.Constants.JSON_TYPE;
import static iudx.resource.server.apiserver.util.Constants.JSON_TITLE;
import static iudx.resource.server.apiserver.util.Constants.ID;
import static iudx.resource.server.apiserver.util.Constants.USER_ID;
import static iudx.resource.server.apiserver.util.Constants.API;
import static iudx.resource.server.apiserver.util.Constants.API_ENDPOINT;
import static iudx.resource.server.common.ResponseUrn.BACKING_SERVICE_FORMAT_URN;
import static iudx.resource.server.database.postgres.Constants.SELECT_UPLOAD_STATUS_SQL;

public class AsyncRestApi {

	private static final Logger LOGGER = LogManager.getLogger(AsyncRestApi.class);

	private final Vertx vertx;
	private final Router router;
	private final PostgresService pgService;
	private MeteringService meteringService;

	AsyncRestApi(Vertx vertx, PostgresService pgService) {
		this.vertx = vertx;
		this.pgService = pgService;
		this.router = Router.router(vertx);
	}

	public Router init() {

		FailureHandler validationsFailureHandler = new FailureHandler();
		ValidationHandler asyncSearchValidationHandler = new ValidationHandler(vertx, RequestType.ASYNC);

		router
				.get(Api.SEARCH.path)
				.handler(asyncSearchValidationHandler)
				.handler(AuthHandler.create(vertx))
				.handler(this::handleAsyncSearchRequest)
				.handler(validationsFailureHandler);

		router
				.get(Api.STATUS.path)
				.handler(asyncSearchValidationHandler)
				.handler(AuthHandler.create(vertx))
				.handler(this::handleAsyncStatusRequest)
				.handler(validationsFailureHandler);

		return router;
	}

	private void handleAsyncSearchRequest(RoutingContext routingContext) {
	}

	private void handleAsyncStatusRequest(RoutingContext routingContext) {
		HttpServerRequest request = routingContext.request();
		HttpServerResponse response = routingContext.response();
		
		String referenceID = request.getParam("referenceID");
		StringBuilder query = new StringBuilder(SELECT_UPLOAD_STATUS_SQL
				.replace("$1",referenceID));

		pgService.executeQuery(query.toString(), pgHandler -> {
			if (pgHandler.succeeded()) {
				String status = pgHandler.result().getString("status");
				String fileDownloadURL = pgHandler.result().getString("URL");
				JsonObject result = new JsonObject()
								.put("status",status);

				if(status.equalsIgnoreCase("ready")) {
					result.put("file-download-url", fileDownloadURL);
				}

				Future.future(fu -> updateAuditTable(routingContext));
				handleSuccessResponse(response, ResponseType.Ok.getCode(), result.toString());
			} else if (pgHandler.failed()) {
				LOGGER.error("Fail: Search Fail");
				processBackendResponse(response, pgHandler.cause().getMessage());
			}
		});
	
	}

	private void handleSuccessResponse(HttpServerResponse response, int statusCode, String result) {
		response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode).end(result);
	}

	private void processBackendResponse(HttpServerResponse response, String failureMessage) {
		LOGGER.debug("Info : " + failureMessage);
		try {
			JsonObject json = new JsonObject(failureMessage);
			int type = json.getInteger(JSON_TYPE);
			HttpStatusCode status = HttpStatusCode.getByValue(type);
			String urnTitle = json.getString(JSON_TITLE);
			ResponseUrn urn;
			if (urnTitle != null) {
				urn = ResponseUrn.fromCode(urnTitle);
			} else {
				urn = ResponseUrn.fromCode(type + "");
			}
			// return urn in body
			response
							.putHeader(CONTENT_TYPE, APPLICATION_JSON)
							.setStatusCode(type)
							.end(generateResponse(status, urn).toString());
		} catch (DecodeException ex) {
			LOGGER.error("ERROR : Expecting Json from backend service [ jsonFormattingException ]");
			handleResponse(response, HttpStatusCode.BAD_REQUEST, BACKING_SERVICE_FORMAT_URN);
		}
	}

	private void handleResponse(HttpServerResponse response, HttpStatusCode code, ResponseUrn urn) {
		handleResponse(response, code, urn, code.getDescription());
	}

	private void handleResponse(
					HttpServerResponse response, HttpStatusCode statusCode, ResponseUrn urn, String message) {
		response
						.putHeader(CONTENT_TYPE, APPLICATION_JSON)
						.setStatusCode(statusCode.getValue())
						.end(generateResponse(statusCode, urn, message).toString());
	}

	private Future<Void> updateAuditTable(RoutingContext context) {
		Promise<Void> promise = Promise.promise();
		JsonObject authInfo = (JsonObject) context.data().get("authInfo");

		JsonObject request = new JsonObject();
		request.put(USER_ID, authInfo.getValue(USER_ID));
		request.put(ID, authInfo.getValue(ID));
		request.put(API, authInfo.getValue(API_ENDPOINT));
		meteringService.executeWriteQuery(
						request,
						handler -> {
							if (handler.succeeded()) {
								LOGGER.info("audit table updated");
								promise.complete();
							} else {
								LOGGER.error("failed to update audit table");
								promise.complete();
							}
						});

		return promise.future();
	}}
