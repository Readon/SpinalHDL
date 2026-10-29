# ADR-0004: SerDes 倍率自动推导，不作对外参数

- Status: Accepted
- Date: 2026-09-14
- Decider: digital-rtl + 用户（grilling Q4 = a）

## Context
OSERDESE3/ISERDESE3 支持 2x / 4x 两种 SERDES 倍率。数据速率与倍率硬约束：
- DDR4-3200：每 pin 1600 Mbps 差分 → 4x 时 SerDes 内部时钟 400 MHz（可闭合）；
  2x 要 800 MHz（几乎闭不上）。**高速只能 4x。**
- DDR3-1600：每 pin 800 Mbps → 2x(400 MHz)/4x(200 MHz) 均可。

候选：
- (a) 自动推导：倍率由 generation + 目标速率算出，不作对外参数。
- (b) 参数化：serdesMult ∈ {2,4} 作显式参数。
- (c) 固定 4x：统一 4x，低速率也 4x。

## Decision
**(a) 自动推导。** 倍率在编译期由 `generation + 目标速率` 计算，非法组合（如
DDR4-3200 配 2x）用 require 在编译期拦截。不作对外参数。

## Rationale
1. 对使用者透明：关心的是“DDR4-3200 能不能跑”，不是“SerDes 几倍”。
2. 避免非法组合（高速 + 2x）流入综合。
3. 与 ADR-0003 参数化框架一致：对外只暴露有意义的规格维度，实现细节内部推导。

## Consequences
- 参数化框架内部新增一个**派生量** `serdesMult`（非对外参数），由速率表驱动。
- 速率表需覆盖各代际各速率点到“所需最小倍率”的映射（DDR4 高速段强制 4x）。
