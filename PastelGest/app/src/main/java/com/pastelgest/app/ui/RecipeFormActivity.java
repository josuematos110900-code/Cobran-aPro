package com.pastelgest.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.pastelgest.app.R;
import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.core.Money;
import com.pastelgest.app.core.RecipeCostCalculator;
import com.pastelgest.app.core.Units;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.model.RecipeIngredientRow;
import com.pastelgest.app.repository.IngredientRepository;
import com.pastelgest.app.repository.RecipeRepository;
import com.pastelgest.app.ui.adapter.Row;
import com.pastelgest.app.util.Async;
import com.pastelgest.app.util.Fmt;
import com.pastelgest.app.util.ImageStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Receita: ingredientes, rendimento, custos e cálculo automático do custo por pastel. */
public class RecipeFormActivity extends BaseActivity {
    private EditText name, description, yield, packaging, other, price;
    private TextInputLayout nameLayout, yieldLayout;
    private ImageView photo;
    private TextView photoHint, costWarning, itemsEmpty;
    private LinearLayout itemsBox, costBox;
    private final List<RecipeRepository.DraftLine> lines = new ArrayList<>();
    private List<Ingredient> ingredients = new ArrayList<>();
    private Recipe editing;
    private String photoPath;
    private ActivityResultLauncher<String> pickImage;
    private int previewToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_form);
        String id = extraId();
        setupToolbar(id == null ? "Nova receita" : "Editar receita", true);
        name = findViewById(R.id.rf_name);
        nameLayout = findViewById(R.id.rf_name_layout);
        description = findViewById(R.id.rf_description);
        yield = findViewById(R.id.rf_yield);
        yieldLayout = findViewById(R.id.rf_yield_layout);
        packaging = findViewById(R.id.rf_packaging);
        other = findViewById(R.id.rf_other);
        price = findViewById(R.id.rf_price);
        photo = findViewById(R.id.rf_photo);
        photoHint = findViewById(R.id.rf_photo_hint);
        costWarning = findViewById(R.id.rf_cost_warning);
        itemsEmpty = findViewById(R.id.rf_items_empty);
        itemsBox = findViewById(R.id.rf_items);
        costBox = findViewById(R.id.rf_cost);
        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            work(() -> ImageStore.copy(this, uri, "recipe"), path -> {
                photoPath = path;
                showPhoto();
            });
        });
        photo.setOnClickListener(v -> pickImage.launch("image/*"));
        findViewById(R.id.rf_photo_remove).setOnClickListener(v -> {
            photoPath = null;
            showPhoto();
        });
        findViewById(R.id.rf_add_item).setOnClickListener(v -> editLine(-1));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        Watcher.on(this::preview, yield, packaging, other, price);
        work(() -> {
            List<Object> out = new ArrayList<>();
            out.add(new IngredientRepository(this).search(""));
            RecipeRepository repo = new RecipeRepository(this);
            Recipe r = id == null ? null : repo.get(id);
            out.add(r);
            out.add(r == null ? null : repo.ingredientRows(r.id));
            return out;
        }, data -> {
            @SuppressWarnings("unchecked") List<Ingredient> ings = (List<Ingredient>) data.get(0);
            ingredients = ings;
            editing = (Recipe) data.get(1);
            if (editing != null) {
                name.setText(editing.name);
                description.setText(editing.description);
                yield.setText(String.valueOf(editing.yieldQty));
                packaging.setText(Money.formatInput(editing.packagingCostCents));
                other.setText(Money.formatInput(editing.otherCostCents));
                price.setText(Money.formatInput(editing.suggestedPriceCents));
                photoPath = editing.photoPath;
                @SuppressWarnings("unchecked") List<RecipeIngredientRow> rows = (List<RecipeIngredientRow>) data.get(2);
                for (RecipeIngredientRow r : rows) {
                    RecipeRepository.DraftLine l = new RecipeRepository.DraftLine();
                    l.ingredientId = r.ri.ingredientId;
                    l.ingredientName = r.ingredientName;
                    l.unitType = r.unitType;
                    l.unit = r.ri.enteredUnit;
                    l.qtyMilli = r.ri.qtyMilli;
                    lines.add(l);
                }
            }
            if (ingredients.isEmpty()) Ui.info(this, "Sem ingredientes", "Registe primeiro os ingredientes no separador Stock.");
            showPhoto();
            renderLines();
        });
    }

    private void showPhoto() {
        boolean has = photoPath != null && new File(photoPath).exists();
        if (has) photo.setImageURI(Uri.fromFile(new File(photoPath)));
        else photo.setImageDrawable(null);
        photoHint.setVisibility(has ? View.GONE : View.VISIBLE);
        findViewById(R.id.rf_photo_remove).setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private void renderLines() {
        itemsBox.removeAllViews();
        for (int i = 0; i < lines.size(); i++) {
            RecipeRepository.DraftLine l = lines.get(i);
            View line = LayoutInflater.from(this).inflate(R.layout.item_line, itemsBox, false);
            ((TextView) line.findViewById(R.id.line_title)).setText(l.ingredientName);
            ((TextView) line.findViewById(R.id.line_subtitle)).setText("Toque para editar");
            ((TextView) line.findViewById(R.id.line_value)).setText(Units.format(l.qtyMilli, l.unit));
            final int idx = i;
            line.setOnClickListener(v -> editLine(idx));
            line.findViewById(R.id.line_remove).setOnClickListener(v -> {
                lines.remove(idx);
                renderLines();
            });
            itemsBox.addView(line);
        }
        itemsEmpty.setVisibility(lines.isEmpty() ? View.VISIBLE : View.GONE);
        preview();
    }

    private void editLine(int index) {
        if (ingredients.isEmpty()) {
            Ui.info(this, "Sem ingredientes", "Registe primeiro os ingredientes no separador Stock.");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_recipe_ingredient, null);
        Dropdown<Ingredient> ing = new Dropdown<>(v.findViewById(R.id.dr_ingredient));
        Dropdown<String> unit = new Dropdown<>(v.findViewById(R.id.dr_unit));
        EditText qty = v.findViewById(R.id.dr_qty);
        TextInputLayout qtyLayout = v.findViewById(R.id.dr_qty_layout);
        ing.setItems(ingredients, i -> i.name);
        ing.setListener(i -> {
            unit.setItems(Arrays.asList(Units.compatible(i.unitType)), u -> u);
            unit.select(i.unitType == Units.TYPE_UNIT ? 0 : 1);
        });
        unit.setListener(qtyLayout::setSuffixText);
        if (index >= 0) {
            RecipeRepository.DraftLine l = lines.get(index);
            ing.selectWhere(i -> i.id.equals(l.ingredientId));
            unit.selectWhere(u -> u.equals(l.unit));
            qty.setText(Units.formatInput(l.qtyMilli, l.unit));
        }
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(index >= 0 ? "Editar ingrediente" : "Adicionar ingrediente")
                .setView(v)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            qtyLayout.setError(null);
            try {
                Ingredient i = ing.selected();
                if (i == null) throw new BusinessException("Seleccione o ingrediente.");
                String u = unit.selected();
                if (u == null) throw new BusinessException("Seleccione a unidade.");
                long q = Units.parseToBaseMilli(Ui.text(qty), u);
                if (q <= 0) throw new BusinessException("A quantidade tem de ser superior a zero.");
                for (int k = 0; k < lines.size(); k++) {
                    if (k != index && lines.get(k).ingredientId.equals(i.id)) {
                        throw new BusinessException("Este ingrediente já está na receita.");
                    }
                }
                RecipeRepository.DraftLine l = new RecipeRepository.DraftLine();
                l.ingredientId = i.id;
                l.ingredientName = i.name;
                l.unitType = i.unitType;
                l.unit = u;
                l.qtyMilli = q;
                if (index >= 0) lines.set(index, l);
                else lines.add(l);
                d.dismiss();
                renderLines();
            } catch (BusinessException e) {
                qtyLayout.setError(e.getMessage());
            }
        }));
        d.show();
    }

    private long yieldValue() {
        try {
            String y = Ui.text(yield);
            return y.isEmpty() ? 0 : Long.parseLong(y.replace(",", ".").split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long moneyOr0(EditText e) {
        try {
            return Money.parseOptional(Ui.text(e));
        } catch (BusinessException ex) {
            return 0;
        }
    }

    /** Pré-visualização do custo, calculada em segundo plano com os custos actuais. */
    private void preview() {
        final long y = yieldValue();
        final long pack = moneyOr0(packaging), oth = moneyOr0(other), pr = moneyOr0(price);
        final List<RecipeRepository.DraftLine> copy = new ArrayList<>(lines);
        final int token = ++previewToken;
        if (y <= 0 || copy.isEmpty()) {
            costBox.removeAllViews();
            Ui.addKv(costBox, "Custo por pastel", "Indique ingredientes e rendimento", Row.TONE_NONE);
            costWarning.setVisibility(View.GONE);
            return;
        }
        Async.run(() -> new RecipeRepository(this).previewCost(copy, y, pack, oth), r -> {
            if (token != previewToken) return;
            renderCost(costBox, r, pr);
            costWarning.setVisibility(r.missingPrices ? View.VISIBLE : View.GONE);
            costWarning.setText("Alguns ingredientes ainda não têm preço (sem compras registadas). O custo está incompleto.");
        }, e -> { });
    }

    static void renderCost(LinearLayout box, RecipeCostCalculator.Result r, long priceCents) {
        box.removeAllViews();
        for (RecipeCostCalculator.Line l : r.lines) {
            Ui.addKv(box, l.name, l.hasCost ? Fmt.money(l.costCents) : "sem preço", l.hasCost ? Row.TONE_NONE : Row.TONE_ORANGE);
        }
        Ui.addKv(box, "Ingredientes", Fmt.money(r.ingredientsCostCents), Row.TONE_NONE);
        Ui.addKv(box, "Embalagem (" + r.yieldQty + " un)", Fmt.money(r.packagingCostCents), Row.TONE_NONE);
        Ui.addKv(box, "Outros custos", Fmt.money(r.otherCostCents), Row.TONE_NONE);
        Ui.addKv(box, "Custo total do lote", Fmt.money(r.totalCostCents), Row.TONE_NONE);
        Ui.addKv(box, "Custo por pastel", Fmt.money(r.unitCostCents), Row.TONE_ACCENT);
        if (priceCents > 0) {
            long margin = priceCents - r.unitCostCents;
            Ui.addKv(box, "Margem por pastel", Fmt.money(margin) + " ("
                    + RecipeCostCalculator.marginPercent(priceCents, r.unitCostCents).toPlainString().replace('.', ',') + "%)",
                    Ui.moneyTone(margin));
        }
    }

    private void save() {
        Ui.clearErrors(nameLayout, yieldLayout);
        try {
            Recipe r = new Recipe();
            r.id = editing == null ? "" : editing.id;
            r.name = Ui.text(name);
            if (r.name.isEmpty()) {
                nameLayout.setError("Indique o nome do pastel.");
                return;
            }
            r.description = Ui.text(description);
            r.yieldQty = yieldValue();
            if (r.yieldQty <= 0) {
                yieldLayout.setError("Indique quantos pastéis a receita produz.");
                return;
            }
            r.packagingCostCents = Money.parseOptional(Ui.text(packaging));
            r.otherCostCents = Money.parseOptional(Ui.text(other));
            r.suggestedPriceCents = Money.parseOptional(Ui.text(price));
            r.photoPath = photoPath;
            List<RecipeRepository.DraftLine> copy = new ArrayList<>(lines);
            work(() -> new RecipeRepository(this).save(r, copy), saved -> {
                Ui.toast(this, "Receita guardada. Custo por pastel: " + Fmt.money(saved.lastUnitCostCents));
                finish();
            });
        } catch (BusinessException e) {
            Ui.showError(this, e);
        }
    }
}
