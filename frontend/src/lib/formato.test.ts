import { afterEach, describe, expect, it, vi } from "vitest"

import { hojeEmBrasilia } from "./formato"

describe("hoje em Brasília", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("é a data de Brasília, como AAAA-MM-DD, mesmo quando em UTC já é o dia seguinte", () => {
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(new Date("2026-01-05T01:30:00Z"))

    expect(hojeEmBrasilia()).toBe("2026-01-04")
  })
})
