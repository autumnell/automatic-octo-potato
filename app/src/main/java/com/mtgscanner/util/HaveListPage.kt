package com.mtgscanner.util

import java.util.Locale

/** One line of a have list. */
data class HaveItem(
    val name: String,
    val setCode: String,
    val number: String,
    val quantity: Int,
    /** Price per copy, or null when unknown. */
    val price: Double?,
    /** Color letters, like "WU"; empty = colorless. */
    val colors: String,
    val imageUrl: String?,
    val foil: Boolean = false,
    val condition: String = "NM",
    val link: String = "",
)

/**
 * A have list as one web page a friend can open in any browser: card pictures, a search box, sort and
 * color filters. Everything is inside the file except the card pictures, which load from Scryfall.
 * The cards are written into the page, so it reads fine even with scripts off.
 */
object HaveListPage {
    fun html(title: String, items: List<HaveItem>, showPrices: Boolean, date: String): String {
        val cards = items.sumOf { it.quantity }
        val total = items.sumOf { (it.price ?: 0.0) * it.quantity }
        val summary = (if (cards == 1) "1 card" else "$cards cards") + (if (showPrices && total > 0) " · ${usd(total)}" else "")
        val rows = items.sortedBy { it.name.lowercase() }.joinToString("\n") { card(it, showPrices) }
        val sortOptions = if (showPrices) {
            """<option value="name">Name</option><option value="price">Price ↓</option><option value="cheap">Price ↑</option><option value="set">Set</option>"""
        } else {
            """<option value="name">Name</option><option value="set">Set</option>"""
        }
        val chips = COLORS.joinToString("") { (k, label) -> """<button type="button" class="chip" data-c="$k" aria-pressed="false" title="$label"><i class="pip p$k"></i></button>""" }
        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>${esc(title)}</title>
<style>
:root{--bg:#f4f4f7;--panel:#fff;--text:#17181c;--muted:#666873;--line:#d3d5dc;--accent:#6b4fd8;--chip:#e9eaef}
@media (prefers-color-scheme:dark){:root{--bg:#111214;--panel:#1a1b1f;--text:#ededef;--muted:#9a9ba3;--line:#34363d;--accent:#a99bff;--chip:#24262c}}
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.4 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
header{padding:20px 16px 8px;max-width:1100px;margin:auto}
h1{margin:0;font-size:22px;font-weight:650}
.sum{color:var(--muted);margin-top:2px}
.bar{position:sticky;top:0;z-index:1;background:var(--bg);padding:8px 16px;max-width:1100px;margin:auto;display:flex;flex-wrap:wrap;gap:8px;align-items:center}
input,select{font:inherit;color:var(--text);background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:8px 10px}
input{flex:1 1 180px;min-width:0}
.chips{display:flex;gap:4px}
.chip{border:1px solid var(--line);background:var(--panel);border-radius:999px;width:34px;height:34px;padding:0;display:grid;place-items:center;cursor:pointer}
.chip[aria-pressed=true]{border-color:var(--accent);box-shadow:0 0 0 2px var(--accent) inset}
.pip{display:block;width:14px;height:14px;border-radius:50%;border:1px solid rgba(0,0,0,.25)}
.pW{background:#f8f3d8}.pU{background:#3d8fd6}.pB{background:#3a3436}.pR{background:#e0533d}.pG{background:#2f9e5b}.pC{background:#b8b3ad}.pM{background:linear-gradient(135deg,#e3b341,#d36ee0)}
main{max-width:1100px;margin:auto;padding:4px 16px 32px;display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px}
.card{background:var(--panel);border-radius:12px;overflow:hidden;display:flex;flex-direction:column;color:inherit;text-decoration:none}
.card img,.ph{width:100%;aspect-ratio:488/680;display:block;background:var(--chip);object-fit:cover}
.ph{display:grid;place-items:center;padding:8px;text-align:center;color:var(--muted);font-size:13px}
.info{padding:8px 10px 10px}
.name{font-weight:600;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.meta{color:var(--muted);font-size:12px;display:flex;justify-content:space-between;gap:6px}
.price{color:var(--accent);font-weight:600}
.empty{grid-column:1/-1;text-align:center;color:var(--muted);padding:40px 0}
footer{color:var(--muted);font-size:12px;text-align:center;padding:0 16px 24px}
</style>
</head>
<body>
<header><h1>${esc(title)}</h1><div class="sum">$summary</div></header>
<div class="bar">
<input id="q" type="search" placeholder="Search" aria-label="Search">
<select id="s" aria-label="Sort">$sortOptions</select>
<div class="chips" id="c">$chips</div>
</div>
<main id="g">
$rows
<p class="empty" id="none" hidden>No matches</p>
</main>
<footer>${esc(date)} · Grimoire</footer>
<script>
(function(){
var g=document.getElementById('g'),q=document.getElementById('q'),s=document.getElementById('s'),none=document.getElementById('none');
var cards=[].slice.call(g.querySelectorAll('.card')),on={};
function apply(){
var t=q.value.trim().toLowerCase(),cs=Object.keys(on).filter(function(k){return on[k]}),n=0;
cards.forEach(function(e){
var c=e.dataset.c,hit=(!t||e.dataset.n.indexOf(t)>=0)&&(!cs.length||cs.some(function(k){return k==='C'?c==='':k==='M'?c.length>1:c.indexOf(k)>=0}));
e.hidden=!hit;if(hit)n++;});
none.hidden=n>0;}
function sort(){
var k=s.value;cards.sort(function(a,b){
if(k==='price')return b.dataset.p-a.dataset.p;
if(k==='cheap')return a.dataset.p-b.dataset.p;
if(k==='set')return a.dataset.s.localeCompare(b.dataset.s)||a.dataset.n.localeCompare(b.dataset.n);
return a.dataset.n.localeCompare(b.dataset.n);});
cards.forEach(function(e){g.insertBefore(e,none);});}
q.addEventListener('input',apply);
s.addEventListener('change',sort);
[].forEach.call(document.querySelectorAll('.chip'),function(b){b.addEventListener('click',function(){
var k=b.dataset.c;on[k]=!on[k];b.setAttribute('aria-pressed',on[k]?'true':'false');apply();});});
})();
</script>
</body>
</html>
"""
    }

    private val COLORS = listOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")

    private fun card(c: HaveItem, showPrices: Boolean): String {
        val tag = if (c.link.startsWith("https://")) "a" else "div"
        val href = if (tag == "a") """ href="${esc(c.link)}" target="_blank" rel="noopener"""" else ""
        val pic = if (c.imageUrl != null && c.imageUrl.startsWith("https://")) {
            """<img loading="lazy" src="${esc(c.imageUrl)}" alt="${esc(c.name)}">"""
        } else {
            """<div class="ph">${esc(c.name)}</div>"""
        }
        val left = buildList {
            // Wishlist and deck lines may have no printing.
            if (c.setCode.isNotBlank()) add(c.setCode.uppercase() + if (c.number.isNotBlank()) " #${c.number}" else "")
            if (c.foil) add("foil")
            if (c.condition.isNotBlank() && c.condition != "NM") add(c.condition)
        }.joinToString(" · ")
        val qty = if (c.quantity > 1) "×${c.quantity} " else ""
        val price = if (showPrices && c.price != null) """<span class="price">${usd(c.price)}</span>""" else ""
        val p = String.format(Locale.US, "%.2f", c.price ?: 0.0)
        val colors = c.colors.uppercase().filter { it in "WUBRG" }
        return """<$tag class="card"$href data-n="${esc(c.name.lowercase())}" data-s="${esc(c.setCode.lowercase())}" data-p="$p" data-c="$colors">$pic<div class="info"><div class="name">${esc(c.name)}</div><div class="meta"><span>${esc(qty + left)}</span>$price</div></div></$tag>"""
    }

    fun usd(v: Double): String = "$" + String.format(Locale.US, "%,.2f", v)

    /** Escapes text for HTML content and quoted attributes. */
    fun esc(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
