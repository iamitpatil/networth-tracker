//
//  APIClient.swift
//  NetWorthTracker
//

import Foundation
import Combine

enum APIError: Error {
    case invalidURL
    case noData
    case decodingError
    case serverError(Int, String)
    case networkError(Error)
    case unauthorized
    case invalidResponse
}

class APIClient {
    static let shared = APIClient()
    
    private let baseURL: String
    private var accessToken: String?
    private let session: URLSession
    
    init(baseURL: String = APIConfig.baseURL) {
        self.baseURL = baseURL
        self.accessToken = UserDefaults.standard.string(forKey: "accessToken")
        
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 300
        self.session = URLSession(configuration: config)
    }
    
    func setAccessToken(_ token: String) {
        self.accessToken = token
        UserDefaults.standard.set(token, forKey: "accessToken")
    }
    
    func clearAccessToken() {
        self.accessToken = nil
        UserDefaults.standard.removeObject(forKey: "accessToken")
    }
    
    func isAuthenticated() -> Bool {
        return accessToken != nil
    }
    
    // MARK: - Generic Request Methods
    
    func get<T: Decodable>(_ path: String) async throws -> T {
        return try await request(method: "GET", path: path, body: nil as EmptyBody?)
    }
    
    func post<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        return try await request(method: "POST", path: path, body: body)
    }
    
    func post<T: Decodable>(_ path: String) async throws -> T {
        return try await request(method: "POST", path: path, body: nil as EmptyBody?)
    }
    
    func put<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        return try await request(method: "PUT", path: path, body: body)
    }
    
    func delete<T: Decodable>(_ path: String) async throws -> T {
        return try await request(method: "DELETE", path: path, body: nil as EmptyBody?)
    }
    
    private struct EmptyBody: Encodable {}
    
    private func request<T: Decodable, B: Encodable>(
        method: String,
        path: String,
        body: B?
    ) async throws -> T {
        guard let url = URL(string: baseURL + path) else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        if let token = accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        if let body = body, !(body is EmptyBody) {
            request.httpBody = try JSONEncoder().encode(body)
        }
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        
        switch httpResponse.statusCode {
        case 200...299:
            if T.self == EmptyResponse.self {
                return EmptyResponse() as! T
            }
            do {
                return try JSONDecoder().decode(T.self, from: data)
            } catch {
                print("Decoding error: \(error)")
                print("Data: \(String(data: data, encoding: .utf8) ?? "N/A")")
                throw APIError.decodingError
            }
        case 401:
            clearAccessToken()
            throw APIError.unauthorized
        case 400...499:
            let errorMessage = String(data: data, encoding: .utf8) ?? "Client error"
            throw APIError.serverError(httpResponse.statusCode, errorMessage)
        case 500...599:
            let errorMessage = String(data: data, encoding: .utf8) ?? "Server error"
            throw APIError.serverError(httpResponse.statusCode, errorMessage)
        default:
            throw APIError.serverError(httpResponse.statusCode, "Unknown error")
        }
    }
    
    struct EmptyResponse: Decodable {}
}