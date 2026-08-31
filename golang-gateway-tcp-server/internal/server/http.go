package server

import (
	"encoding/json"
	"log"
	"net/http"

	"golang-gateway-tcp-server/internal/service"
)

type HTTPServer struct {
	addr     string
	executor *service.UpgradeExecutor
}

type apiResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data"`
}

func NewHTTPServer(addr string, executor *service.UpgradeExecutor) *HTTPServer {
	return &HTTPServer{addr: addr, executor: executor}
}

func (s *HTTPServer) ListenAndServe() error {
	mux := http.NewServeMux()
	mux.HandleFunc("/internal/device/upgrade-request", s.handleUpgradeRequest)
	mux.HandleFunc("/internal/device/cancel-request", s.handleCancelRequest)
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, apiResponse{Code: 0, Message: "success", Data: "UP"})
	})
	log.Printf("HTTP server starting on %s", s.addr)
	return http.ListenAndServe(s.addr, mux)
}

func (s *HTTPServer) handleUpgradeRequest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, apiResponse{Code: -1, Message: "method not allowed"})
		return
	}
	var req service.PlatformUpgradeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, apiResponse{Code: -1, Message: err.Error()})
		return
	}
	if err := s.executor.SendUpgradeRequest(r.Context(), req); err != nil {
		writeJSON(w, http.StatusOK, apiResponse{Code: -1, Message: err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, apiResponse{Code: 0, Message: "success", Data: nil})
}

func (s *HTTPServer) handleCancelRequest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, apiResponse{Code: -1, Message: "method not allowed"})
		return
	}
	var req service.PlatformCancelUpgradeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, apiResponse{Code: -1, Message: err.Error()})
		return
	}
	if err := s.executor.SendCancelUpgradeRequest(r.Context(), req); err != nil {
		writeJSON(w, http.StatusOK, apiResponse{Code: -1, Message: err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, apiResponse{Code: 0, Message: "success", Data: nil})
}

func writeJSON(w http.ResponseWriter, status int, payload apiResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}
