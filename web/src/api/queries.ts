import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  apiFetch,
  type AuthResponse,
  type CompleteSessionResponse,
  type Language,
  type Lesson,
  type QueueResponse,
  type SessionDto,
  type Stats,
  type Unit,
} from "./client";

export function useLanguages() {
  return useQuery({
    queryKey: ["languages"],
    queryFn: () => apiFetch<Language[]>("/api/languages"),
    staleTime: 60 * 60 * 1000,
  });
}

export function useUnits(languageId: number | null) {
  return useQuery({
    queryKey: ["units", languageId],
    queryFn: () => apiFetch<Unit[]>(`/api/languages/${languageId}/units`),
    enabled: languageId != null,
    staleTime: 60 * 60 * 1000,
  });
}

export function useUnit(unitId: number | null) {
  return useQuery({
    queryKey: ["unit", unitId],
    queryFn: () => apiFetch<Unit>(`/api/units/${unitId}`),
    enabled: unitId != null,
    staleTime: 60 * 60 * 1000,
  });
}

export function useLesson(lessonId: number | null) {
  return useQuery({
    queryKey: ["lesson", lessonId],
    queryFn: () => apiFetch<Lesson>(`/api/lessons/${lessonId}`),
    enabled: lessonId != null,
    staleTime: 60 * 60 * 1000,
  });
}

export function useQueue() {
  return useQuery({
    queryKey: ["queue"],
    queryFn: () => apiFetch<QueueResponse>("/api/learners/me/queue"),
    staleTime: 30 * 1000,
  });
}

export function useStats() {
  return useQuery({
    queryKey: ["stats"],
    queryFn: () => apiFetch<Stats>("/api/learners/me/stats"),
    staleTime: 30 * 1000,
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { email: string; password: string; displayName: string }) =>
      apiFetch<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue"] });
      queryClient.invalidateQueries({ queryKey: ["stats"] });
    },
  });
}

export function useLogin() {
  return useMutation({
    mutationFn: (payload: { email: string; password: string }) =>
      apiFetch<AuthResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  });
}

export function useStartSession() {
  return useMutation({
    mutationFn: (lessonId: number) =>
      apiFetch<SessionDto>("/api/sessions", {
        method: "POST",
        body: JSON.stringify({ lessonId }),
      }),
  });
}

export function useCompleteSession() {
  return useMutation({
    mutationFn: (sessionId: number) =>
      apiFetch<CompleteSessionResponse>(`/api/sessions/${sessionId}/complete`, {
        method: "POST",
      }),
  });
}
