# Screen Price Manager – Lontum's Phone Repairs

An offline Android app for storing screen purchase prices from suppliers and working out the customer price automatically.

## The 8 sections (open the menu at the top left)
1. **Dashboard** shows models saved, purchases, profit this month, stock value, the margin summary, recently updated prices and the most purchased screens.
2. **Search models** finds models by part of the name, the brand or a common misspelling ("techno", "galaxy a15", "camon20"). Only models you saved are shown. If nothing matches, you can add the model.
3. **Add purchase** records the model, quality, supplier, price, quantity, date and notes. Every purchase is kept, so your price history is never replaced. The recommended customer price appears as you type.
4. **Inventory** shows stock per model and quality, stock value and low stock. You can record damaged screens, screens returned to the supplier, screens added back, and count corrections.
5. **Sales & profits** shows profit for today, this week and this month, and lets you record, edit and delete sales.
6. **Suppliers** holds contact details, call and WhatsApp buttons, the purchases from each supplier, and a price comparison against other suppliers.
7. **Reports** shows totals, profit by month, the most profitable screens and the most sold screens.
8. **Settings** covers:
   - Percentage markup or fixed profit, with rounding to 100, 500 or 1,000.
   - Different margins for each quality.
   - A test calculator.
   - Stock settings and the light or dark theme.
   - Export to Excel (CSV), backup, and restore.

The app has no sample prices. Every price in it is one you enter.

## Building with GitHub
1. Upload everything inside this folder to a GitHub repository.
2. Go to **Actions**, choose **"set up a workflow yourself"**, paste in the contents of `COPY-THIS-build-workflow.yml`, then tap **Commit changes**.
3. When the build finishes, download the APK from the **Releases** page.

Keep `app/screenprice.keystore`. It makes every new APK install as an update so your data is kept.
