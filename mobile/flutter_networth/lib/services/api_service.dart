// lib/services/api_service.dart
import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';
import 'package:flutter_networth/models/user_model.dart';
import 'package:flutter_networth/models/holding_model.dart';
import 'package:flutter_networth/models/networth_model.dart';
import 'package:flutter_networth/models/transaction_model.dart';
import 'package:flutter_networth/models/goal_model.dart';

part 'api_service.g.dart';

@RestApi(baseUrl: 'http://localhost:8080/api/v1')
abstract class ApiService {
  factory ApiService(Dio dio, {String baseUrl}) = _ApiService;

  // Auth
  @POST('/auth/login')
  Future<AuthResponse> login(@Body() LoginRequest request);

  @POST('/auth/register')
  Future<AuthResponse> register(@Body() RegisterRequest request);

  @POST('/auth/refresh')
  Future<AuthResponse> refreshToken();

  // Dashboard
  @GET('/net-worth/breakdown')
  Future<NetWorthBreakdown> getNetWorthBreakdown();

  @GET('/net-worth/health-score')
  Future<HealthScore> getHealthScore();

  @GET('/net-worth/history')
  Future<List<NetWorthHistory>> getNetWorthHistory(
    @Query('days') int days,
  );

  // Holdings
  @GET('/portfolio/holdings')
  Future<List<Holding>> getHoldings();

  @POST('/portfolio/holdings')
  Future<Holding> createHolding(@Body() CreateHoldingRequest request);

  @DELETE('/portfolio/holdings/{id}')
  Future<void> deleteHolding(@Path('id') String id);

  @GET('/portfolio/holdings/{id}/price-history')
  Future<List<Map<String, dynamic>>> getPriceHistory(
    @Path('id') String id,
    @Query('days') int days,
  );

  // Transactions
  @GET('/portfolio/transactions')
  Future<List<Transaction>> getTransactions();

  @POST('/portfolio/transactions')
  Future<Transaction> createTransaction(@Body() CreateTransactionRequest request);

  // Goals
  @GET('/goals')
  Future<List<Goal>> getGoals();

  @POST('/goals')
  Future<Goal> createGoal(@Body() CreateGoalRequest request);

  @PUT('/goals/{id}')
  Future<Goal> updateGoal(
    @Path('id') String id,
    @Body() CreateGoalRequest request,
  );

  @DELETE('/goals/{id}')
  Future<void> deleteGoal(@Path('id') String id);

  // Market Data
  @POST('/market/refresh-prices')
  Future<void> refreshPrices();

  @GET('/symbols')
  Future<List<Map<String, dynamic>>> getSymbols();
}